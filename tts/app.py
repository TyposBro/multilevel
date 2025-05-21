# {PATH_TO_PROJECT}/tts/app.py
# In app.py (Flask service for Kokoro)
import os
import io
import logging
import base64 # <-- Import base64
from flask import Flask, request, jsonify # No send_file needed
from kokoro import KPipeline
import torch
import soundfile as sf
import numpy as np # Need numpy if concatenating

# ... (Configuration, Logging, Flask App Init, Pipeline Init remain the same) ...
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
DEFAULT_LANG_CODE = 'a'
DEFAULT_VOICE = 'af_heart'
PORT = 5005
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)
app = Flask(__name__)
pipeline = None
try:
    logger.info(f"Initializing Kokoro pipeline on device: {DEVICE}...")
    pipeline = KPipeline(lang_code=DEFAULT_LANG_CODE, device=DEVICE)
    logger.info("Kokoro pipeline initialized successfully.")
except Exception as e:
    logger.error(f"Fatal error initializing Kokoro pipeline: {e}", exc_info=True)
    exit(1)


@app.route('/synthesize', methods=['POST'])
def synthesize_speech():
    if not pipeline:
         logger.error("Synthesize request received, but pipeline is not available.")
         return jsonify({"error": "TTS service not ready"}), 503

    data = request.get_json()
    if not data or 'text' not in data:
        logger.warning("Received request without 'text' field.")
        return jsonify({"error": "Missing 'text' field in JSON body"}), 400

    text_to_synthesize = data['text']
    voice = data.get('voice', DEFAULT_VOICE)
    speed = data.get('speed', 1.0)
    lang_code = data.get('lang_code', DEFAULT_LANG_CODE)

    logger.info(f"Received synthesis request: voice='{voice}', speed={speed}, lang='{lang_code}', text='{text_to_synthesize[:50]}...'")

    try:
        if lang_code != pipeline.default_lang_code:
             logger.warning(f"Requested lang_code '{lang_code}' differs from pipeline default '{pipeline.default_lang_code}'.")

        full_audio = []
        generator = pipeline(
            text_to_synthesize, voice=voice, speed=speed, split_pattern=r'\n+'
        )
        for i, (gs, ps, audio_chunk) in enumerate(generator):
            # logger.debug(f"Generated chunk {i}: graphemes='{gs}', phonemes='{ps[:30]}...'")
            if isinstance(audio_chunk, torch.Tensor):
                full_audio.append(audio_chunk.squeeze().cpu().numpy())
            elif isinstance(audio_chunk, (list, np.ndarray)):
                 full_audio.append(np.array(audio_chunk).squeeze())
            else:
                 logger.warning(f"Unexpected audio chunk type: {type(audio_chunk)}")

        if not full_audio:
            logger.warning("Kokoro generated no audio chunks.")
            return jsonify({"error": "TTS generation resulted in empty audio"}), 500

        final_audio_np = np.concatenate(full_audio) if len(full_audio) > 1 else full_audio[0]

        # --- Encode Audio to Base64 ---
        buffer = io.BytesIO()
        sf.write(buffer, final_audio_np, pipeline.sr, format='WAV', subtype='PCM_16')
        buffer.seek(0)
        audio_bytes = buffer.read()
        audio_base64 = base64.b64encode(audio_bytes).decode('utf-8') # Encode and convert to string

        logger.info("Synthesis successful. Returning Base64 audio.")

        # --- Return JSON with Base64 Audio ---
        return jsonify({
            "audioContent": audio_base64, # Base64 encoded WAV audio
            "audioConfig": { # Include info client might need
                "sampleRateHertz": pipeline.sr,
                "encoding": "WAV_PCM16" # Indicate format
            }
        })

    except ValueError as ve:
        logger.error(f"ValueError during synthesis: {ve}", exc_info=True)
        return jsonify({"error": f"Invalid parameter: {ve}"}), 400
    except Exception as e:
        logger.error(f"Error during synthesis: {e}", exc_info=True)
        return jsonify({"error": "Internal server error during TTS synthesis"}), 500

# ... (health check and app run remain the same) ...
@app.route('/health', methods=['GET'])
def health_check():
    status = {"status": "ok", "pipeline_ready": pipeline is not None}
    status_code = 200 if pipeline else 503
    return jsonify(status), status_code

if __name__ == '__main__':
    logger.info(f"Starting Kokoro TTS service on port {PORT}")
    app.run(host='0.0.0.0', port=PORT, debug=False)