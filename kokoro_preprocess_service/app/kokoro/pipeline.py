# {PATH_TO_PROJECT}/g2p/src/pipeline.py
# (or kokoro/pipeline.py)

from .model import KModel
from dataclasses import dataclass
from huggingface_hub import hf_hub_download
from loguru import logger
# from misaki import en, espeak # Moved misaki imports to be more conditional or within methods
from typing import Callable, Generator, List, Optional, Tuple, Union
import re
import torch
import os
import json # Added for loading config
from pathlib import Path

ALIASES = {
    'en-us': 'a',
    'en-gb': 'b',
    'es': 'e',
    'fr-fr': 'f',
    'hi': 'h',
    'it': 'i',
    'pt-br': 'p',
    # 'ja': 'j', # REMOVED
    # 'zh': 'z', # REMOVED
}

LANG_CODES = dict(
    # pip install misaki[en]
    a='American English',
    b='British English',

    # espeak-ng
    e='es',
    f='fr-fr',
    h='hi',
    i='it',
    p='pt-br',

    # pip install misaki[ja] # Comment out or remove these lines
    # j='Japanese', # REMOVED

    # pip install misaki[zh] # Comment out or remove these lines
    # z='Mandarin Chinese', # REMOVED
)

LOCAL_CONFIG_BASE_PATH = Path(__file__).parent / "configs"
    
class KPipeline:
    def __init__(
        self,
        lang_code: str,
        repo_id: Optional[str] = None,
        generate_audio: bool = False,
    ):
        if generate_audio:
            raise ValueError("This KPipeline configuration is for preprocessing (input_ids) only. generate_audio must be False.")

        self.repo_id = repo_id or "hexgrad/Kokoro-82M"

        lang_code = lang_code.lower()
        lang_code = ALIASES.get(lang_code, lang_code)
        # This assertion will now correctly fail if 'j' or 'z' are somehow passed,
        # as they are removed from LANG_CODES.
        assert lang_code in LANG_CODES, (f"Unsupported language code '{lang_code}'. "
                                          f"Supported codes are: {list(LANG_CODES.keys())}")
        self.lang_code = lang_code
        self.generate_audio = False

        self.vocab = None
        self.context_length = None
        self._loaded_config_data = None

        local_config_filename = self.repo_id.replace('/', '_').replace('-', '_') + '_config.json'
        local_config_path = LOCAL_CONFIG_BASE_PATH / local_config_filename
        
        if local_config_path.exists():
            logger.info(f"Loading bundled config from local path: {local_config_path}")
            with open(local_config_path, 'r', encoding='utf-8') as r:
                self._loaded_config_data = json.load(r)
        else:
            logger.warning(f"Local config {local_config_path} not found. Falling back to hf_hub_download for repo_id: {self.repo_id}")
            try:
                downloaded_config_path = hf_hub_download(repo_id=self.repo_id, filename='config.json')
                with open(downloaded_config_path, 'r', encoding='utf-8') as r:
                    self._loaded_config_data = json.load(r)
            except Exception as e:
                logger.error(f"Failed to load config.json from local path ({local_config_path}) AND from Hugging Face Hub ({self.repo_id}): {e}")
                raise ValueError(
                    f"Could not load configuration for '{self.repo_id}'. "
                    f"Ensure '{local_config_filename}' is bundled or '{self.repo_id}' is a valid Hugging Face repo."
                ) from e

        if not self._loaded_config_data:
             raise RuntimeError(f"Configuration data could not be loaded for {self.repo_id}")

        self.vocab = self._loaded_config_data.get('vocab')
        plbert_config = self._loaded_config_data.get('plbert', {})
        self.context_length = plbert_config.get('max_position_embeddings')

        if self.vocab is None:
            raise ValueError(f"Vocabulary not found in loaded config for {self.repo_id}")
        if self.context_length is None:
            raise ValueError(f"Context length (max_position_embeddings) not found in plbert config for {self.repo_id}")

        self.model: Optional[KModel] = None
        self.voices = {}

        if lang_code in 'ab':
            try:
                from misaki import en, espeak # Specific import for English
                fallback = espeak.EspeakFallback(british=lang_code=='b')
            except Exception as e:
                logger.warning(f"EspeakFallback not Enabled for G2P lang {lang_code}: OOD words will be skipped. Error: {e}")
                fallback = None
            self.g2p = en.G2P(trf=False, british=lang_code=='b', fallback=fallback, unk='')
        # The 'j' and 'z' blocks will not be reached if they are removed from LANG_CODES
        # and PRELOAD_PIPELINES, so the problematic imports won't occur.
        elif lang_code == 'j': # This block should ideally not be reached
            # from misaki import ja # This line would cause error if lang_code 'j' was still allowed
            # self.g2p = ja.JAG2P()
            # This case should be prevented by the LANG_CODES check earlier
            raise NotImplementedError("Japanese (j) support is currently disabled.")
        elif lang_code == 'z': # This block should ideally not be reached
            # from misaki import zh # This line would cause error if lang_code 'z' was still allowed
            # self.g2p = zh.ZHG2P(...)
            # This case should be prevented by the LANG_CODES check earlier
            raise NotImplementedError("Chinese (z) support is currently disabled.")
        else: # For other espeak-supported languages
            language = LANG_CODES[lang_code]
            logger.warning(f"Using EspeakG2P(language='{language}').")
            from misaki import espeak # Specific import for espeak
            self.g2p = espeak.EspeakG2P(language=language)
        logger.info(f"KPipeline initialized for lang='{self.lang_code}', repo_id='{self.repo_id}' (using bundled/downloaded config)")

    # ... (rest of the KPipeline class remains the same) ...
    # Make sure to include all methods:
    # _phonemes_to_input_ids, load_single_voice, load_voice, tokens_to_ps,
    # waterfall_last, tokens_to_text, en_tokenize, infer, generate_from_tokens,
    # join_timestamps, Result (dataclass), __call__

    def _phonemes_to_input_ids(self, ps: str) -> Optional[torch.LongTensor]:
        if not ps:
            return None
        
        input_ids_list = list(filter(lambda i: i is not None, map(lambda p: self.vocab.get(p), ps)))
        
        max_phoneme_tokens = self.context_length - 2 
        if len(input_ids_list) > max_phoneme_tokens:
            logger.warning(
                f"Phoneme string (first 30 chars: '{ps[:30]}...') resulted in {len(input_ids_list)} tokens. "
                f"This exceeds the maximum of {max_phoneme_tokens} (context length {self.context_length} - 2 for BOS/EOS). "
                f"Truncating tokens."
            )
            input_ids_list = input_ids_list[:max_phoneme_tokens]
            
        bos_token_id = 0 
        eos_token_id = 0
        final_input_ids = torch.LongTensor([[bos_token_id, *input_ids_list, eos_token_id]])
        
        return final_input_ids

    def load_single_voice(self, voice: str):
        # This method is for audio generation, which is disabled in this setup.
        # However, keeping it for structural integrity if KPipeline is used elsewhere.
        if voice in self.voices:
            return self.voices[voice]
        if voice.endswith('.pt'):
            f = voice
        else:
            f = hf_hub_download(repo_id=self.repo_id, filename=f'voices/{voice}.pt')
            if not voice.startswith(self.lang_code):
                v = LANG_CODES.get(voice[0], voice) 
                p = LANG_CODES.get(self.lang_code, self.lang_code)
                logger.warning(f'Language mismatch, loading {v} voice into {p} pipeline.')
        pack = torch.load(f, weights_only=True)
        self.voices[voice] = pack
        return pack

    def load_voice(self, voice: Union[str, torch.FloatTensor], delimiter: str = ",") -> torch.FloatTensor:
        # Also for audio generation.
        if isinstance(voice, torch.FloatTensor):
            return voice
        if voice in self.voices:
            return self.voices[voice]
        logger.debug(f"Loading voice: {voice}")
        packs = [self.load_single_voice(v) for v in voice.split(delimiter)]
        if len(packs) == 1:
            self.voices[voice] = packs[0] 
            return packs[0]
        self.voices[voice] = torch.mean(torch.stack(packs), dim=0)
        return self.voices[voice]

    @staticmethod
    def tokens_to_ps(tokens: List['en.MToken']) -> str: # Use forward reference for en.MToken if misaki.en not globally imported
        return ''.join(t.phonemes + (' ' if t.whitespace else '') for t in tokens).strip()

    @staticmethod
    def waterfall_last(
        tokens: List['en.MToken'],
        next_count: int,
        waterfall: List[str] = ['!.?…', ':;', ',—'],
        bumps: List[str] = [')', '”']
    ) -> int:
        for w in waterfall:
            z = next((i for i, t in reversed(list(enumerate(tokens))) if t.phonemes in set(w)), None)
            if z is None:
                continue
            z += 1
            if z < len(tokens) and tokens[z].phonemes in bumps:
                z += 1
            if next_count - len(KPipeline.tokens_to_ps(tokens[:z])) <= 510:
                return z
        return len(tokens)

    @staticmethod
    def tokens_to_text(tokens: List['en.MToken']) -> str:
        return ''.join(t.text + t.whitespace for t in tokens).strip()

    def en_tokenize(
        self,
        tokens: List['en.MToken'] # Assuming en.MToken type is available when lang_code is 'a' or 'b'
    ) -> Generator[Tuple[str, str, List['en.MToken']], None, None]:
        tks = []
        pcount = 0
        for t in tokens:
            t.phonemes = '' if t.phonemes is None else t.phonemes
            next_ps = t.phonemes + (' ' if t.whitespace else '')
            next_pcount = pcount + len(next_ps.rstrip())
            if next_pcount > 510: 
                z = KPipeline.waterfall_last(tks, next_pcount)
                text = KPipeline.tokens_to_text(tks[:z])
                logger.debug(f"Chunking text at {z}: '{text[:30]}{'...' if len(text) > 30 else ''}'")
                ps = KPipeline.tokens_to_ps(tks[:z])
                yield text, ps, tks[:z]
                tks = tks[z:]
                pcount = len(KPipeline.tokens_to_ps(tks))
                if not tks:
                    next_ps = next_ps.lstrip()
            tks.append(t)
            pcount += len(next_ps)
        if tks:
            text = KPipeline.tokens_to_text(tks)
            ps = KPipeline.tokens_to_ps(tks)
            yield ''.join(text).strip(), ''.join(ps).strip(), tks

    @staticmethod
    def infer(
        model: KModel,
        ps: str,
        pack: torch.FloatTensor, # Or List[torch.FloatTensor] depending on actual use
        speed: Union[float, Callable[[int], float]] = 1
    ) -> KModel.Output:
        if callable(speed):
            speed = speed(len(ps))
        # Ensure pack is correctly indexed if it's a list based on phoneme length
        actual_pack = pack[min(len(ps)-1, len(pack)-1)] if isinstance(pack, list) else pack
        return model(ps, actual_pack, speed, return_output=True)


    def generate_from_tokens(
        self,
        tokens: Union[str, List['en.MToken']], # Forward reference for en.MToken
        voice: Optional[str] = None, 
        speed: float = 1,
        model: Optional[KModel] = None
    ) -> Generator['KPipeline.Result', None, None]:
        
        if self.generate_audio:
            # This block will not be entered due to generate_audio=False
            effective_model = model or self.model
            if not effective_model:
                raise ValueError("Audio generation requested, but no KModel is available.")
            if voice is None:
                raise ValueError('Specify a voice for audio generation.')
            
            pack_data = self.load_voice(voice) # Assuming this is the correct structure (e.g., list or tensor)
            # Adapt pack_data if it needs to be device-specific or a list
            if isinstance(pack_data, torch.Tensor):
                 pack = pack_data.to(effective_model.device)
            elif isinstance(pack_data, list): # If load_voice returns list of tensors
                 pack = [p.to(effective_model.device) for p in pack_data]
            else:
                 raise TypeError("Unsupported voice pack type")


            if isinstance(tokens, str): 
                ps = tokens
                output = KPipeline.infer(effective_model, ps, pack, speed)
                yield self.Result(graphemes='', phonemes=ps, output=output)
                return
            
            for gs, ps, tks_chunk in self.en_tokenize(tokens): # Assumes tokens is List[en.MToken]
                if not ps:
                    continue
                output = KPipeline.infer(effective_model, ps, pack, speed)
                if output.pred_dur is not None:
                    KPipeline.join_timestamps(tks_chunk, output.pred_dur)
                yield self.Result(graphemes=gs, phonemes=ps, tokens=tks_chunk, output=output)
        else: # Not generating audio
            if isinstance(tokens, str): 
                ps = tokens
                final_input_ids = self._phonemes_to_input_ids(ps)
                if final_input_ids is not None:
                    yield self.Result(graphemes='', phonemes=ps, input_ids=final_input_ids)
                return

            for gs, ps, tks_chunk in self.en_tokenize(tokens): # Assumes tokens is List[en.MToken]
                if not ps:
                    continue
                final_input_ids = self._phonemes_to_input_ids(ps)
                if final_input_ids is not None:
                    yield self.Result(graphemes=gs, phonemes=ps, tokens=tks_chunk, input_ids=final_input_ids)

    @staticmethod
    def join_timestamps(tokens: List['en.MToken'], pred_dur: torch.LongTensor): # Forward reference
        MAGIC_DIVISOR = 80
        if not tokens or len(pred_dur) < 3:
            return
        left = right = 2 * max(0, pred_dur[0].item() - 3)
        i = 1
        for t in tokens:
            if i >= len(pred_dur)-1:
                break
            if not t.phonemes:
                continue 
            
            num_phonemes_in_token = len(t.phonemes)
            
            if i + num_phonemes_in_token > len(pred_dur) -1 : 
                logger.warning(f"Timestamp assignment: not enough pred_dur values for token '{t.text}'. Skipping.")
                break 

            t.start_ts = left / MAGIC_DIVISOR
            token_dur = pred_dur[i : i + num_phonemes_in_token].sum().item()
            
            space_dur = 0
            if t.whitespace:
                if i + num_phonemes_in_token < len(pred_dur) - 1:
                    space_dur = pred_dur[i + num_phonemes_in_token].item()
                else:
                    logger.warning(f"Timestamp assignment: no pred_dur for space after token '{t.text}'.")

            left = right + (2 * token_dur) + space_dur 
            t.end_ts = left / MAGIC_DIVISOR 
            right = left + space_dur 
            
            i += num_phonemes_in_token + (1 if t.whitespace else 0)


    @dataclass
    class Result:
        graphemes: str
        phonemes: str
        tokens: Optional[List['en.MToken']] = None # Forward reference
        output: Optional[KModel.Output] = None
        input_ids: Optional[torch.LongTensor] = None 
        text_index: Optional[int] = None

        @property
        def audio(self) -> Optional[torch.FloatTensor]:
            return None if self.output is None else self.output.audio

        @property
        def pred_dur(self) -> Optional[torch.LongTensor]:
            return None if self.output is None else self.output.pred_dur

        def __iter__(self):
            yield self.graphemes
            yield self.phonemes
            yield self.audio 

        def __getitem__(self, index):
            return [self.graphemes, self.phonemes, self.audio][index]

        def __len__(self):
            return 3
            
    def __call__(
        self,
        text: Union[str, List[str]],
        voice: Optional[str] = None, 
        speed: Union[float, Callable[[int], float]] = 1.0,
        split_pattern: Optional[str] = r'\n+',
        model: Optional[KModel] = None 
    ) -> Generator['KPipeline.Result', None, None]:
        
        # Since generate_audio is False, audio generation parts are effectively skipped.
        if self.generate_audio:
             # This block will not be run
             pass

        if isinstance(text, str):
            segments = re.split(split_pattern, text.strip()) if split_pattern else [text]
        else: 
            segments = text
            
        for graphemes_index, graphemes_segment_text in enumerate(segments):
            if not graphemes_segment_text.strip():
                continue
                
            if self.lang_code in 'ab':
                # Import misaki.en only when needed for English processing
                from misaki import en as misaki_en # Alias to avoid conflict if 'en' is a var
                logger.debug(f"Processing English text segment: {graphemes_segment_text[:50]}{'...' if len(graphemes_segment_text) > 50 else ''}")
                _, mtokens_list_for_segment = self.g2p(graphemes_segment_text) # type: ignore
                
                for gs_chunk, ps_chunk, tks_chunk in self.en_tokenize(mtokens_list_for_segment):
                    if not ps_chunk: 
                        continue
                    
                    # No audio generation, directly yield input_ids
                    final_input_ids = self._phonemes_to_input_ids(ps_chunk)
                    if final_input_ids is not None:
                        yield self.Result(
                            graphemes=gs_chunk, 
                            phonemes=ps_chunk, 
                            tokens=tks_chunk, 
                            input_ids=final_input_ids,
                            text_index=graphemes_index
                        )
            
            else: # Non-English (but not 'j' or 'z' as they are now excluded)
                logger.debug(f"Processing non-English text segment ({self.lang_code}): {graphemes_segment_text[:50]}{'...' if len(graphemes_segment_text) > 50 else ''}")
                chunk_size = 400 
                text_sub_chunks_for_segment = []
                sentences = re.split(r'([.!?]+)', graphemes_segment_text)
                current_text_sub_chunk = ""
                
                for i in range(0, len(sentences), 2):
                    sentence_part = sentences[i]
                    if i + 1 < len(sentences): 
                        sentence_part += sentences[i + 1]
                        
                    if len(current_text_sub_chunk) + len(sentence_part) <= chunk_size:
                        current_text_sub_chunk += sentence_part
                    else:
                        if current_text_sub_chunk:
                            text_sub_chunks_for_segment.append(current_text_sub_chunk.strip())
                        current_text_sub_chunk = sentence_part
                
                if current_text_sub_chunk: 
                    text_sub_chunks_for_segment.append(current_text_sub_chunk.strip())
                
                if not text_sub_chunks_for_segment and graphemes_segment_text.strip():
                    if len(graphemes_segment_text) > chunk_size:
                         text_sub_chunks_for_segment = [graphemes_segment_text[i:i+chunk_size] for i in range(0, len(graphemes_segment_text), chunk_size)]
                    else: 
                        text_sub_chunks_for_segment = [graphemes_segment_text.strip()]

                for grapheme_sub_chunk_text in text_sub_chunks_for_segment:
                    if not grapheme_sub_chunk_text.strip():
                        continue
                        
                    ps_chunk, _ = self.g2p(grapheme_sub_chunk_text) # type: ignore
                    if not ps_chunk: 
                        continue
                    
                    final_input_ids = self._phonemes_to_input_ids(ps_chunk)
                    if final_input_ids is not None:
                        yield self.Result(
                            graphemes=grapheme_sub_chunk_text, 
                            phonemes=ps_chunk, 
                            input_ids=final_input_ids,
                            text_index=graphemes_index
                        )