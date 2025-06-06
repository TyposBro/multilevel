# app/main.py
from fastapi import FastAPI, HTTPException, Body
from pydantic import BaseModel, Field
from typing import List, Optional, Union, Dict, Tuple
import torch
import os

from kokoro.pipeline import KPipeline, LANG_CODES
from kokoro.model import KModel # For KPipeline.Result type hints if any

# --- Configuration ---
# (lang_code, local_config_alias_or_hf_repo_id)
PRELOAD_PIPELINES: List[Tuple[str, Optional[str]]] = [
    ("a", "hexgrad/Kokoro-82M"),  # Will look for hexgrad_Kokoro_82M_config.json locally first
    ("j", "hexgrad/Kokoro-82M"),
    ("z", "hexgrad/Kokoro-82M-v1.1-zh"), # Will look for hexgrad_Kokoro_82M_v1_1_zh_config.json
]
# This default is now more of a key for the local config, or a fallback HF ID
DEFAULT_LOCAL_CONFIG_KEY = "hexgrad/Kokoro-82M"

app = FastAPI(
    title="Kokoro Preprocessing API (Bundled Config)",
    description="API for Kokoro text-to-phoneme-to-input_ids preprocessing with bundled configurations.",
    version="0.3.0"
)

pipeline_cache: Dict[Tuple[str, str], KPipeline] = {}

def get_pipeline(lang_code: str, config_key: Optional[str] = None) -> KPipeline:
    actual_config_key = config_key or DEFAULT_LOCAL_CONFIG_KEY
    cache_key = (lang_code, actual_config_key)
    if cache_key not in pipeline_cache:
        print(f"Initializing KPipeline for lang='{lang_code}', config_key='{actual_config_key}' (input_ids only)")
        try:
            pipeline_cache[cache_key] = KPipeline(
                lang_code=lang_code,
                repo_id=actual_config_key, # KPipeline will try local load based on this
                generate_audio=False,
            )
        except Exception as e:
            print(f"Error initializing pipeline {cache_key}: {e}")
            import traceback
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=f"Could not initialize Kokoro pipeline for {lang_code}, config_key={actual_config_key}. Error: {e}")
    return pipeline_cache[cache_key]

@app.on_event("startup")
async def startup_event():
    print("Starting up and preloading pipelines (input_ids mode, bundled config)...")
    for lang, config_key_override in PRELOAD_PIPELINES:
        try:
            get_pipeline(lang, config_key_override)
            print(f"Successfully preloaded KPipeline for lang='{lang}', config_key='{config_key_override or DEFAULT_LOCAL_CONFIG_KEY}'")
        except Exception as e:
            print(f"Failed to preload KPipeline for lang='{lang}', config_key='{config_key_override or DEFAULT_LOCAL_CONFIG_KEY}': {e}")
    print("Startup complete.")

# --- Pydantic Models (same as before for preprocessing-only) ---
class PreprocessRequest(BaseModel):
    text: Union[str, List[str]] = Field(..., description="Text or list of text segments to process.")
    lang_code: str = Field(..., description=f"Language code. Supported: {list(LANG_CODES.keys())}")
    split_pattern: Optional[str] = Field(r'\n+', description="Regex pattern to split text if a single string is provided.")
    config_key: Optional[str] = Field(None, description=f"Configuration key (e.g., 'hexgrad/Kokoro-82M' or 'hexgrad/Kokoro-82M-v1.1-zh') to use for vocab/context_length. Defaults to '{DEFAULT_LOCAL_CONFIG_KEY}'. KPipeline will look for a bundled config matching this key or fall back to Hugging Face Hub if it's a valid repo ID.")

class ResultItem(BaseModel):
    graphemes: str
    phonemes: str
    text_index: Optional[int] = None
    input_ids: Optional[List[List[int]]] = Field(None, description="Model input_ids. Shape: [1, sequence_length].")

class PreprocessResponse(BaseModel):
    results: List[ResultItem]
    lang_code_used: str
    config_key_used: str

# --- API Endpoints (mostly same as before) ---
@app.post("/preprocess", response_model=PreprocessResponse)
async def preprocess_text_endpoint(request: PreprocessRequest = Body(...)):
    if request.lang_code not in LANG_CODES:
        raise HTTPException(status_code=400, detail=f"Unsupported language_code: {request.lang_code}. Supported: {list(LANG_CODES.keys())}")

    try:
        pipeline = get_pipeline(request.lang_code, request.config_key)
    except HTTPException as e:
        raise e
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Unexpected error getting pipeline: {e}")

    output_results: List[ResultItem] = []
    try:
        generator = pipeline(
            text=request.text,
            split_pattern=request.split_pattern,
        )
        for res_item in generator:
            item_data = {
                "graphemes": res_item.graphemes,
                "phonemes": res_item.phonemes,
                "text_index": res_item.text_index,
            }
            if res_item.input_ids is not None:
                item_data["input_ids"] = res_item.input_ids.tolist()
            else:
                item_data["input_ids"] = None
            output_results.append(ResultItem(**item_data))
    except ValueError as ve:
        raise HTTPException(status_code=400, detail=str(ve))
    except Exception as e:
        print(f"Error during pipeline processing: {type(e).__name__}: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"An error occurred during processing: {e}")

    return PreprocessResponse(
        results=output_results,
        lang_code_used=request.lang_code,
        config_key_used=request.config_key or DEFAULT_LOCAL_CONFIG_KEY
    )

@app.get("/health")
async def health_check():
    return {"status": "ok", "torch_version": torch.__version__}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)