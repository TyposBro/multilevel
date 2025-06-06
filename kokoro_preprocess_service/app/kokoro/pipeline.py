
# {PATH_TO_PROJECT}/g2p/src/pipeline.py

from .model import KModel
from dataclasses import dataclass
from huggingface_hub import hf_hub_download
from loguru import logger
from misaki import en, espeak
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
    'ja': 'j',
    'zh': 'z',
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

    # pip install misaki[ja]
    j='Japanese',

    # pip install misaki[zh]
    z='Mandarin Chinese',
)

LOCAL_CONFIG_BASE_PATH = Path(__file__).parent / "configs"


    
class KPipeline:
    '''
    KPipeline is a language-aware support class with main responsibility:
    Perform language-specific G2P, mapping (and chunking) text -> phonemes

    You are expected to have one KPipeline per language. If you have multiple
    KPipelines, you should reuse one KModel instance across all of them if generating audio.

    KPipeline is designed to work with a KModel for audio generation, but this is not required
    if only phonemes or input_ids are needed.
    There are 2 ways to pass an existing model into a pipeline for audio generation:
    1. On init: us_pipeline = KPipeline(lang_code='a', model=model)
    2. On call: us_pipeline(text, voice, model=model)

    By default, if audio generation is enabled, KPipeline will automatically initialize its own KModel.
    To get preprocessed input_ids instead of audio, initialize with `generate_audio=False`.
    '''
    def __init__(
        self,
        lang_code: str,
        repo_id: Optional[str] = None, # This can now be a local alias or an HF repo_id
        # model, trf, en_callable, device are less relevant or handled differently now
        generate_audio: bool = False, # Forcing this for preprocessing
        # No KModel instance loading logic needed here for preprocessing-only
    ):
        if generate_audio:
            raise ValueError("This KPipeline configuration is for preprocessing (input_ids) only. generate_audio must be False.")

        # Default repo_id now refers to a potential local config key or an HF model name
        self.repo_id = repo_id or "hexgrad/Kokoro-82M" # This will be used to find local config or download

        lang_code = lang_code.lower()
        lang_code = ALIASES.get(lang_code, lang_code)
        assert lang_code in LANG_CODES, (lang_code, LANG_CODES)
        self.lang_code = lang_code
        self.generate_audio = False # Hardcoded

        self.vocab = None
        self.context_length = None
        self._loaded_config_data = None

        # Try to load config locally first
        # We'll use a convention for local config file names, e.g., repo_id.replace('/', '_') + '_config.json'
        local_config_filename = self.repo_id.replace('/', '_').replace('-', '_') + '_config.json'
        local_config_path = LOCAL_CONFIG_BASE_PATH / local_config_filename
        
        if local_config_path.exists():
            logger.info(f"Loading bundled config from local path: {local_config_path}")
            with open(local_config_path, 'r', encoding='utf-8') as r:
                self._loaded_config_data = json.load(r)
        else:
            logger.warning(f"Local config {local_config_path} not found. Falling back to hf_hub_download for repo_id: {self.repo_id}")
            # Fallback to Hugging Face Hub download if local config not found
            # This allows flexibility if a new repo_id is specified that isn't bundled
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
        # Adjust key if necessary based on actual config structure for context_length
        plbert_config = self._loaded_config_data.get('plbert', {})
        self.context_length = plbert_config.get('max_position_embeddings')

        if self.vocab is None:
            raise ValueError(f"Vocabulary not found in loaded config for {self.repo_id}")
        if self.context_length is None:
            raise ValueError(f"Context length (max_position_embeddings) not found in plbert config for {self.repo_id}")

        self.model: Optional[KModel] = None # No KModel instance needed
        self.voices = {} # Not used if not generating audio

        # G2P setup (existing logic, ensure misaki and espeak are available)
        if lang_code in 'ab':
            try:
                from misaki import en, espeak # Moved import here for clarity
                fallback = espeak.EspeakFallback(british=lang_code=='b')
            except Exception as e:
                logger.warning(f"EspeakFallback not Enabled for G2P lang {lang_code}: OOD words will be skipped. Error: {e}")
                fallback = None
            self.g2p = en.G2P(trf=False, british=lang_code=='b', fallback=fallback, unk='')
        elif lang_code == 'j':
            try:
                from misaki import ja
                self.g2p = ja.JAG2P()
            except ImportError:
                logger.error("You need to `pip install misaki[ja]` to use lang_code='j'")
                raise
        elif lang_code == 'z':
            try:
                from misaki import zh
                # Assuming en_callable might be needed by ZHG2P from your original code
                # For simplicity, if you have a default English G2P setup, you could pass it.
                # Or make it optional / handle it if ZHG2P needs it.
                # For this bundled example, we'll assume ZHG2P can init without it or with a default.
                self.g2p = zh.ZHG2P(
                    version=None if self.repo_id == 'hexgrad/Kokoro-82M' else '1.1', # Example logic
                    en_callable=None # Or provide a default English G2P callable if needed by ZHG2P
                )
            except ImportError:
                logger.error("You need to `pip install misaki[zh]` to use lang_code='z'")
                raise
        else:
            language = LANG_CODES[lang_code]
            logger.warning(f"Using EspeakG2P(language='{language}').")
            from misaki import espeak # Moved import here
            self.g2p = espeak.EspeakG2P(language=language)
        logger.info(f"KPipeline initialized for lang='{self.lang_code}', repo_id='{self.repo_id}' (using bundled/downloaded config)")

    def _phonemes_to_input_ids(self, ps: str) -> Optional[torch.LongTensor]:
        if not ps:
            return None
        
        input_ids_list = list(filter(lambda i: i is not None, map(lambda p: self.vocab.get(p), ps)))
        
        max_phoneme_tokens = self.context_length - 2 # For BOS and EOS
        if len(input_ids_list) > max_phoneme_tokens:
            logger.warning(
                f"Phoneme string (first 30 chars: '{ps[:30]}...') resulted in {len(input_ids_list)} tokens. "
                f"This exceeds the maximum of {max_phoneme_tokens} (context length {self.context_length} - 2 for BOS/EOS). "
                f"Truncating tokens."
            )
            input_ids_list = input_ids_list[:max_phoneme_tokens]
            
        bos_token_id = 0 
        eos_token_id = 0
        # Shape: (batch_size=1, sequence_length)
        final_input_ids = torch.LongTensor([[bos_token_id, *input_ids_list, eos_token_id]])
        
        return final_input_ids

    def load_single_voice(self, voice: str):
        if voice in self.voices:
            return self.voices[voice]
        if voice.endswith('.pt'):
            f = voice
        else:
            f = hf_hub_download(repo_id=self.repo_id, filename=f'voices/{voice}.pt')
            if not voice.startswith(self.lang_code):
                v = LANG_CODES.get(voice[0], voice) # Use first char of voice for lang hint
                p = LANG_CODES.get(self.lang_code, self.lang_code)
                logger.warning(f'Language mismatch, loading {v} voice into {p} pipeline.')
        pack = torch.load(f, weights_only=True)
        self.voices[voice] = pack
        return pack

    def load_voice(self, voice: Union[str, torch.FloatTensor], delimiter: str = ",") -> torch.FloatTensor:
        if isinstance(voice, torch.FloatTensor):
            return voice
        if voice in self.voices:
            return self.voices[voice]
        logger.debug(f"Loading voice: {voice}")
        packs = [self.load_single_voice(v) for v in voice.split(delimiter)]
        if len(packs) == 1:
            self.voices[voice] = packs[0] # Cache even single voice under potentially composite name
            return packs[0]
        self.voices[voice] = torch.mean(torch.stack(packs), dim=0)
        return self.voices[voice]

    @staticmethod
    def tokens_to_ps(tokens: List[en.MToken]) -> str:
        return ''.join(t.phonemes + (' ' if t.whitespace else '') for t in tokens).strip()

    @staticmethod
    def waterfall_last(
        tokens: List[en.MToken],
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
            # Use context_length (max tokens) as the true limit, not hardcoded 510 phoneme chars
            # This check is heuristic; true token count happens after vocab mapping.
            # Keeping 510 as a loose char-based proxy for now.
            if next_count - len(KPipeline.tokens_to_ps(tokens[:z])) <= 510: # TODO: Re-evaluate 510 against actual token limits
                return z
        return len(tokens)

    @staticmethod
    def tokens_to_text(tokens: List[en.MToken]) -> str:
        return ''.join(t.text + t.whitespace for t in tokens).strip()

    def en_tokenize(
        self,
        tokens: List[en.MToken]
    ) -> Generator[Tuple[str, str, List[en.MToken]], None, None]:
        tks = []
        pcount = 0
        for t in tokens:
            t.phonemes = '' if t.phonemes is None else t.phonemes
            next_ps = t.phonemes + (' ' if t.whitespace else '')
            next_pcount = pcount + len(next_ps.rstrip())
             # Using a large phoneme char limit (e.g. 510) as a proxy for model's token limit.
             # Actual tokenization and truncation against context_length happens later.
            if next_pcount > 510: # Heuristic phoneme char limit
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
        pack: torch.FloatTensor,
        speed: Union[float, Callable[[int], float]] = 1
    ) -> KModel.Output:
        if callable(speed):
            speed = speed(len(ps)) # Adjust speed based on phoneme length if callable
        
        # KModel.forward handles the phoneme-to-input_ids conversion and assertion against context_length
        # The `ps` string itself should not be excessively long due to prior chunking.
        # If ps implies more tokens than context_length, KModel.forward will assert.
        # The KPipeline's 510 char limit on ps is a preemptive measure.
        return model(ps, pack[min(len(ps)-1, pack.shape[0]-1)], speed, return_output=True)


    def generate_from_tokens(
        self,
        tokens: Union[str, List[en.MToken]],
        voice: Optional[str] = None, # Optional: only needed if self.generate_audio
        speed: float = 1,
        model: Optional[KModel] = None # Optional: call-time model override for audio
    ) -> Generator['KPipeline.Result', None, None]:
        
        if self.generate_audio:
            effective_model = model or self.model
            if not effective_model:
                raise ValueError("Audio generation requested, but no KModel is available. Initialize KPipeline with a model or pass one at call time.")
            if voice is None:
                raise ValueError('Specify a voice for audio generation: pipeline.generate_from_tokens(..., voice="af_heart")')
            
            pack = self.load_voice(voice).to(effective_model.device)

            if isinstance(tokens, str): # Raw phoneme string
                ps = tokens
                if len(ps) > self.context_length * 5: # Very generous heuristic, KModel will assert exact token length
                    logger.warning(f'Phoneme string is very long: {len(ps)} characters. May exceed model context length.')
                # KModel.forward will handle actual token limit assertion.
                output = KPipeline.infer(effective_model, ps, pack, speed)
                yield self.Result(graphemes='', phonemes=ps, output=output)
                return
            
            # List[en.MToken] - typically for English
            for gs, ps, tks in self.en_tokenize(tokens):
                if not ps:
                    continue
                # The 510 char limit in en_tokenize is a safeguard. KModel checks true token limit.
                output = KPipeline.infer(effective_model, ps, pack, speed)
                if output.pred_dur is not None:
                    KPipeline.join_timestamps(tks, output.pred_dur)
                yield self.Result(graphemes=gs, phonemes=ps, tokens=tks, output=output)
        else: # Not generating audio, yield input_ids
            if isinstance(tokens, str): # Raw phoneme string
                ps = tokens
                final_input_ids = self._phonemes_to_input_ids(ps)
                if final_input_ids is not None:
                    yield self.Result(graphemes='', phonemes=ps, input_ids=final_input_ids)
                return

            # List[en.MToken]
            for gs, ps, tks in self.en_tokenize(tokens):
                if not ps:
                    continue
                final_input_ids = self._phonemes_to_input_ids(ps)
                if final_input_ids is not None:
                    yield self.Result(graphemes=gs, phonemes=ps, tokens=tks, input_ids=final_input_ids)

    @staticmethod
    def join_timestamps(tokens: List[en.MToken], pred_dur: torch.LongTensor):
        MAGIC_DIVISOR = 80
        if not tokens or len(pred_dur) < 3:
            return
        left = right = 2 * max(0, pred_dur[0].item() - 3)
        i = 1
        for t in tokens:
            if i >= len(pred_dur)-1:
                break
            if not t.phonemes:
                if t.whitespace:
                    # This logic might need review if pred_dur doesn't account for space-only tokens well
                    # Assuming pred_dur has entries for phonemes and potential space markers
                    # If a token has no phonemes but has whitespace, it might correspond to a 'space' phoneme's duration
                    # This depends on how G2P and KModel handle spaces in pred_dur
                    # For safety, if it's a non-phoneme token, skip duration assignment unless explicitly handled.
                    # Original code advanced `i` twice for whitespace, this might be an error if pred_dur doesn't map that way.
                    # Let's be conservative: if a token has no phonemes, it gets no specific duration from pred_dur here.
                    pass # No phonemes, no duration assignment from this loop.
                continue # Skip to next token if current one has no phonemes.
            
            # Ensure we don't go out of bounds for pred_dur based on phoneme length
            # pred_dur includes BOS, phoneme tokens, EOS. So length is num_phoneme_tokens + 2.
            # `i` starts at 1 (after BOS). `j` points to the start of EOS or space after last phoneme.
            num_phonemes_in_token = len(t.phonemes) # Assuming t.phonemes is a flat string here
            
            if i + num_phonemes_in_token > len(pred_dur) -1 : # -1 for EOS
                logger.warning(f"Timestamp assignment: not enough pred_dur values for token '{t.text}'. Skipping.")
                break 

            t.start_ts = left / MAGIC_DIVISOR
            token_dur = pred_dur[i : i + num_phonemes_in_token].sum().item()
            
            # Duration for space after token
            space_dur = 0
            if t.whitespace:
                # Check if there's a duration value available for the space
                if i + num_phonemes_in_token < len(pred_dur) - 1:
                    space_dur = pred_dur[i + num_phonemes_in_token].item()
                else:
                    logger.warning(f"Timestamp assignment: no pred_dur for space after token '{t.text}'.")

            left = right + (2 * token_dur) + space_dur # Original logic for left
            t.end_ts = left / MAGIC_DIVISOR # Original logic for end_ts
            right = left + space_dur # Original logic for right
            
            i += num_phonemes_in_token + (1 if t.whitespace else 0)


    @dataclass
    class Result:
        graphemes: str
        phonemes: str
        tokens: Optional[List[en.MToken]] = None
        output: Optional[KModel.Output] = None
        input_ids: Optional[torch.LongTensor] = None # New field for token IDs
        text_index: Optional[int] = None # Index of the original text segment

        @property
        def audio(self) -> Optional[torch.FloatTensor]:
            return None if self.output is None else self.output.audio

        @property
        def pred_dur(self) -> Optional[torch.LongTensor]:
            return None if self.output is None else self.output.pred_dur

        ### MARK: BEGIN BACKWARD COMPAT ###
        def __iter__(self):
            yield self.graphemes
            yield self.phonemes
            yield self.audio # Will be None if audio not generated

        def __getitem__(self, index):
            # Note: self.input_ids is not part of this backward compatible access
            return [self.graphemes, self.phonemes, self.audio][index]

        def __len__(self):
            return 3
        #### MARK: END BACKWARD COMPAT ####
    def __call__(
        self,
        text: Union[str, List[str]],
        voice: Optional[str] = None, 
        speed: Union[float, Callable[[int], float]] = 1.0,
        split_pattern: Optional[str] = r'\n+',
        model: Optional[KModel] = None 
    ) -> Generator['KPipeline.Result', None, None]:
        
        effective_model: Optional[KModel] = None
        # pack: Optional[torch.FloatTensor] = None # pack is now a list of tensors from dummy load_voice
        pack_list: Optional[List[torch.FloatTensor]] = None


        if self.generate_audio:
            effective_model = model or self.model
            if not effective_model:
                raise ValueError(
                    "Audio generation requested (self.generate_audio=True), but no KModel is available. "
                    "Initialize KPipeline with a model, pass one at call time, or set generate_audio=False."
                )
            if voice is None:
                raise ValueError(
                    'Specify a voice for audio generation: pipeline(text="Hello world!", voice="af_heart")'
                )
            
            # Assuming load_voice returns a list of tensors, one for each possible phoneme length
            # This matches how KModel's ref_s / pack system works.
            # Our dummy load_voice was simplified, let's assume it returns a list.
            loaded_voice_data = self.load_voice(voice) # This should be the list
            if not isinstance(loaded_voice_data, list) or not all(isinstance(t, torch.Tensor) for t in loaded_voice_data):
                 # If load_voice returns a single tensor due to simplification, wrap it in a list
                 # This part is adapting to the simplified dummy `load_voice`. A real `load_voice`
                 # would already return the correct structure (list of tensors).
                if isinstance(loaded_voice_data, torch.Tensor) and self.context_length:
                    logger.warning("load_voice returned a single tensor; expected list. Adapting for dummy setup.")
                    pack_list = [loaded_voice_data.to(effective_model.device) for _ in range(self.context_length)]
                else:
                    raise TypeError(f"load_voice returned unexpected type: {type(loaded_voice_data)}. Expected List[torch.Tensor].")
            else:
                 pack_list = [t.to(effective_model.device) for t in loaded_voice_data]
        
        # Convert input text to a list of segments
        if isinstance(text, str):
            segments = re.split(split_pattern, text.strip()) if split_pattern else [text]
        else: # text is already List[str]
            segments = text
            
        # Process each segment
        for graphemes_index, graphemes_segment_text in enumerate(segments):
            if not graphemes_segment_text.strip():  # Skip empty segments
                continue
                
            # English processing path ('a' or 'b')
            if self.lang_code in 'ab':
                logger.debug(f"Processing English text segment: {graphemes_segment_text[:50]}{'...' if len(graphemes_segment_text) > 50 else ''}")
                # self.g2p for English returns (raw_text, List[en.MToken])
                _, mtokens_list_for_segment = self.g2p(graphemes_segment_text) # type: ignore
                
                # en_tokenize further chunks the List[MToken] based on phoneme length heuristics
                for gs_chunk, ps_chunk, tks_chunk in self.en_tokenize(mtokens_list_for_segment):
                    if not ps_chunk: # Skip if phonemization resulted in empty string
                        continue
                    
                    current_speed = speed(len(ps_chunk)) if callable(speed) else speed

                    if self.generate_audio:
                        assert effective_model is not None and pack_list is not None, "Model or pack not ready for audio generation"
                        kmodel_output = KPipeline.infer(effective_model, ps_chunk, pack_list, current_speed)
                        if kmodel_output.pred_dur is not None: # KModel.Output may have pred_dur as None
                            KPipeline.join_timestamps(tks_chunk, kmodel_output.pred_dur)
                        yield self.Result(
                            graphemes=gs_chunk, 
                            phonemes=ps_chunk, 
                            tokens=tks_chunk, 
                            output=kmodel_output, 
                            text_index=graphemes_index
                        )
                    else: # Not generating audio, create input_ids
                        final_input_ids = self._phonemes_to_input_ids(ps_chunk)
                        if final_input_ids is not None:
                            yield self.Result(
                                graphemes=gs_chunk, 
                                phonemes=ps_chunk, 
                                tokens=tks_chunk, 
                                input_ids=final_input_ids,
                                text_index=graphemes_index
                            )
            
            # Non-English processing path
            else:
                logger.debug(f"Processing non-English text segment: {graphemes_segment_text[:50]}{'...' if len(graphemes_segment_text) > 50 else ''}")
                # Chunking logic for non-English text based on character count / sentence boundaries
                chunk_size = 400 # Character-based chunk size for non-English
                text_sub_chunks_for_segment = []
                
                sentences = re.split(r'([.!?]+)', graphemes_segment_text)
                current_text_sub_chunk = ""
                
                for i in range(0, len(sentences), 2):
                    sentence_part = sentences[i]
                    if i + 1 < len(sentences): # Add the punctuation back if it exists
                        sentence_part += sentences[i + 1]
                        
                    if len(current_text_sub_chunk) + len(sentence_part) <= chunk_size:
                        current_text_sub_chunk += sentence_part
                    else:
                        if current_text_sub_chunk:
                            text_sub_chunks_for_segment.append(current_text_sub_chunk.strip())
                        current_text_sub_chunk = sentence_part
                
                if current_text_sub_chunk: # Add any remaining part
                    text_sub_chunks_for_segment.append(current_text_sub_chunk.strip())
                
                if not text_sub_chunks_for_segment and graphemes_segment_text.strip():
                    if len(graphemes_segment_text) > chunk_size: # Fallback to fixed-size chunking if very long and no sentence breaks
                         text_sub_chunks_for_segment = [graphemes_segment_text[i:i+chunk_size] for i in range(0, len(graphemes_segment_text), chunk_size)]
                    else: # Otherwise, the segment itself is the chunk
                        text_sub_chunks_for_segment = [graphemes_segment_text.strip()]

                for grapheme_sub_chunk_text in text_sub_chunks_for_segment:
                    if not grapheme_sub_chunk_text.strip():
                        continue
                        
                    ps_chunk, _ = self.g2p(grapheme_sub_chunk_text) # type: ignore
                    if not ps_chunk: 
                        continue
                    
                    current_speed = speed(len(ps_chunk)) if callable(speed) else speed

                    if self.generate_audio:
                        assert effective_model is not None and pack_list is not None, "Model or pack not ready for audio generation"
                        if len(ps_chunk) > 510: # Heuristic from original code
                             logger.warning(
                                 f"Truncating long non-English phoneme string (len {len(ps_chunk)} > 510) "
                                 f"for text chunk: '{grapheme_sub_chunk_text[:30]}...'"
                             )
                             ps_chunk = ps_chunk[:510]
                        kmodel_output = KPipeline.infer(effective_model, ps_chunk, pack_list, current_speed)
                        yield self.Result(
                            graphemes=grapheme_sub_chunk_text, 
                            phonemes=ps_chunk, 
                            output=kmodel_output, 
                            text_index=graphemes_index
                        )
                    else: # Not generating audio, create input_ids
                        final_input_ids = self._phonemes_to_input_ids(ps_chunk)
                        if final_input_ids is not None:
                            yield self.Result(
                                graphemes=grapheme_sub_chunk_text, 
                                phonemes=ps_chunk, 
                                input_ids=final_input_ids,
                                text_index=graphemes_index
                            )