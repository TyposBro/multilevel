package com.typosbro.multilevel.features.whisper.engine;

import android.content.Context;
import android.util.Log;

public class WhisperEngineNative implements WhisperEngine {
    static {
        Log.i("WhisperEngineNative", "Attempting to load native library 'audioEngine'...");
        System.loadLibrary("audioEngine");
        Log.i("WhisperEngineNative", "Native library 'audioEngine' loaded successfully.");
    }

    private final String TAG = "WhisperEngineNative";
    private long nativePtr;
    private boolean mIsInitialized = false;

    public WhisperEngineNative(Context context) {
        nativePtr = createTFLiteEngine();
        // ADDED: Log pointer creation
        Log.d(TAG, "Native engine created. Pointer: " + nativePtr);
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) {
        if (nativePtr == 0) {
            Log.e(TAG, "Cannot initialize, native pointer is zero.");
            return false;
        }
        Log.d(TAG, "Initializing model with path: " + modelPath);
        int ret = loadModel(modelPath, multilingual);
        Log.i(TAG, "Native model loaded with result code: " + ret);
        mIsInitialized = (ret == 0);
        return mIsInitialized;
    }

    @Override
    public void deinitialize() {
        Log.d(TAG, "De-initializing native engine. Pointer: " + nativePtr);
        if (nativePtr != 0) {
            freeModel();
            // Your original code was missing the call to destroy the engine object itself.
            // Assuming you have a destroyTFLiteEngine(nativePtr) native method. If not, this is a leak.
            // For now, we will just zero the pointer as in your code.
            Log.d(TAG, "Freed native model resources.");
            nativePtr = 0;
        }
        mIsInitialized = false;
        Log.i(TAG, "Native engine de-initialized.");
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        if (!mIsInitialized || nativePtr == 0) {
            Log.w(TAG, "transcribeBuffer called but engine not initialized or released. Pointer: " + nativePtr);
            return "";
        }

        try {
            Log.d(TAG, "Calling native transcribeBuffer with " + samples.length + " samples.");
            String result = transcribeBuffer(nativePtr, samples);

            if (result == null) {
                Log.w(TAG, "Native transcribeBuffer returned null. This may indicate an error in JNI layer.");
                return "";
            }
            Log.d(TAG, "Native transcribeBuffer successful.");
            return result;

        } catch (Throwable t) {
            Log.e(TAG, "FATAL: Native 'transcribeBuffer' call threw an exception.", t);
            return "";
        }
    }

    @Override
    public String transcribeFile(String waveFile) {
        if (!mIsInitialized || nativePtr == 0) {
            Log.w(TAG, "transcribeFile called but engine not initialized or released. Pointer: " + nativePtr);
            return "";
        }

        try {
            Log.d(TAG, "Calling native transcribeFile with path: " + waveFile);
            String result = transcribeFile(nativePtr, waveFile);
            if (result == null) {
                Log.w(TAG, "Native transcribeFile returned null.");
                return "";
            }
            Log.d(TAG, "Native transcribeFile successful.");
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "FATAL: Native 'transcribeFile' call threw an exception.", t);
            return "";
        }
    }

    private int loadModel(String modelPath, boolean isMultilingual) {
        return loadModel(nativePtr, modelPath, isMultilingual);
    }

    private void freeModel() {
        freeModel(nativePtr);
    }

    // --- Native methods ---
    private native long createTFLiteEngine();

    private native int loadModel(long nativePtr, String modelPath, boolean isMultilingual);

    private native void freeModel(long nativePtr);

    private native String transcribeBuffer(long nativePtr, float[] samples);

    private native String transcribeFile(long nativePtr, String waveFile);
}