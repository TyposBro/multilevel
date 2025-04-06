import os
import io
import logging
from flask import Flask, request, jsonify, send_file
from kokoro import KPipeline
import torch # Make sure torch is imported
import soundfile as sf # For handling audio data

# --- Configuration ---
# Set device (CPU is usually fine for Kokoro, change to 'cuda' if GPU is set up)
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
DEFAULT_LANG_CODE = 'a' # American English ('a') or British English ('b'), etc.
DEFAULT_VOICE = 'af_heart' # Default voice preset
PORT = 5005 # Port for this Flask service (make sure it's free)

# --- Logging Setup ---
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# --- Flask App Initialization ---
app = Flask(__name__)

# --- Load Kokoro Pipeline (Load once on startup) ---
pipeline = None
try:
    logger.info(f"Initializing Kokoro pipeline on device: {DEVICE}...")
    # Initialize pipeline - this might download models on first run
    # Consider explicitly setting cache directory if needed: cache_dir='/path/to/cache'
    pipeline = KPipeline(lang_code=DEFAULT_LANG_CODE, device=DEVICE)
    logger.info("Kokoro pipeline initialized successfully.")
except Exception as e:
    logger.error(f"Fatal error initializing Kokoro pipeline: {e}", exc_info=True)
    # Exit if pipeline fails to load, as the service is useless without it
    exit(1)

# --- API Endpoint ---
@app.route('/synthesize', methods=['POST'])
def synthesize_speech():
    if not pipeline:
         logger.error("Synthesize request received, but pipeline is not available.")
         return jsonify({"error": "TTS service not ready"}), 503 # Service Unavailable

    # Get text from JSON request body
    data = request.get_json()
    if not data or 'text' not in data:
        logger.warning("Received request without 'text' field.")
        return jsonify({"error": "Missing 'text' field in JSON body"}), 400

    text_to_synthesize = data['text']
    # Get optional parameters or use defaults
    voice = data.get('voice', DEFAULT_VOICE)
    speed = data.get('speed', 1.0)
    lang_code = data.get('lang_code', DEFAULT_LANG_CODE) # Allow overriding language per request

    logger.info(f"Received synthesis request: voice='{voice}', speed={speed}, lang='{lang_code}', text='{text_to_synthesize[:50]}...'")

    try:
        # Check if language code needs pipeline re-init (simple approach)
        # NOTE: A more robust approach might involve multiple pipeline instances or dynamic loading.
        # For now, we assume the default language or expect lang_code to match the initial one.
        if lang_code != pipeline.default_lang_code:
             logger.warning(f"Requested lang_code '{lang_code}' differs from pipeline default '{pipeline.default_lang_code}'. Synthesis might fail or use default.")
             # Ideally, you'd re-initialize or have multiple pipelines if supporting multiple languages frequently.

        # Use Kokoro pipeline to generate audio
        # We process the generator fully here to get the complete audio.
        # For very long texts, streaming might be better but adds complexity.
        full_audio = []
        generator = pipeline(
            text_to_synthesize,
            voice=voice,
            speed=speed,
            split_pattern=r'\n+' # Or None to process as one block
        )

        for i, (gs, ps, audio_chunk) in enumerate(generator):
            logger.debug(f"Generated chunk {i}: graphemes='{gs}', phonemes='{ps[:30]}...'")
            if isinstance(audio_chunk, torch.Tensor):
                # Ensure tensor is on CPU and converted to numpy for soundfile
                full_audio.append(audio_chunk.squeeze().cpu().numpy())
            elif isinstance(audio_chunk, (list, np.ndarray)): # Handle potential numpy array directly
                 full_audio.append(np.array(audio_chunk).squeeze())
            else:
                 logger.warning(f"Unexpected audio chunk type: {type(audio_chunk)}")


        if not full_audio:
            logger.warning("Kokoro generated no audio chunks.")
            return jsonify({"error": "TTS generation resulted in empty audio"}), 500

        # Concatenate chunks if necessary (Kokoro might yield one chunk for shorter text)
        final_audio_np = np.concatenate(full_audio) if len(full_audio) > 1 else full_audio[0]


        # --- Return Audio ---
        # Use soundfile to write WAV data to an in-memory buffer
        buffer = io.BytesIO()
        sf.write(buffer, final_audio_np, pipeline.sr, format='WAV', subtype='PCM_16')
        buffer.seek(0) # Reset buffer position to the beginning

        logger.info("Synthesis successful. Returning WAV audio.")

        # Send the buffer content as a file download with appropriate MIME type
        return send_file(
            buffer,
            mimetype='audio/wav',
            as_attachment=False # Serve inline, not as download
            # download_name='output.wav' # Optional if as_attachment=True
        )

    except ValueError as ve:
        # Catch specific errors like unknown voice
        logger.error(f"ValueError during synthesis: {ve}", exc_info=True)
        return jsonify({"error": f"Invalid parameter: {ve}"}), 400
    except Exception as e:
        logger.error(f"Error during synthesis: {e}", exc_info=True)
        return jsonify({"error": "Internal server error during TTS synthesis"}), 500


# --- Health Check Endpoint (Good Practice) ---
@app.route('/health', methods=['GET'])
def health_check():
    # Basic check: is the pipeline object loaded?
    status = {"status": "ok", "pipeline_ready": pipeline is not None}
    status_code = 200 if pipeline else 503
    return jsonify(status), status_code

# --- Run Flask App ---
if __name__ == '__main__':
    # Run on 0.0.0.0 to be accessible externally (within server's network)
    # Use debug=False for production deployments
    logger.info(f"Starting Kokoro TTS service on port {PORT}")
    app.run(host='0.0.0.0', port=PORT, debug=False)