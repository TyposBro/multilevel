// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/features/whisper/engine/WhisperEngineNative.java
package com.typosbro.multilevel.features.whisper.engine;

import android.content.Context;
import android.util.Log;

public class WhisperEngineNative implements WhisperEngine {
    private final String TAG = "WhisperEngineNative";
    private final long nativePtr;
    private boolean mIsInitialized = false;

    public WhisperEngineNative(Context context) {
        nativePtr = createTFLiteEngine();
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) {
        // This native implementation seems to bundle vocab logic, so only modelPath is needed here.
        int ret = loadModel(modelPath, multilingual);
        Log.d(TAG, "Native model loaded: " + modelPath + ", result: " + ret);
        mIsInitialized = (ret == 0); // Assuming 0 is success
        return mIsInitialized;
    }

    @Override
    public void deinitialize() {
        freeModel();
        mIsInitialized = false;
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        if (!mIsInitialized) return "";
        return transcribeBuffer(nativePtr, samples);
    }

    @Override
    public String transcribeFile(String waveFile) {
        if (!mIsInitialized) return "";
        return transcribeFile(nativePtr, waveFile);
    }

    private int loadModel(String modelPath, boolean isMultilingual) {
        return loadModel(nativePtr, modelPath, isMultilingual);
    }

    private void freeModel() {
        freeModel(nativePtr);
    }

    static {
        // This must match the 'target_name' in the native code's CMakeLists.txt
        System.loadLibrary("audioEngine");
    }

    // Native methods
    private native long createTFLiteEngine();
    private native int loadModel(long nativePtr, String modelPath, boolean isMultilingual);
    private native void freeModel(long nativePtr);
    private native String transcribeBuffer(long nativePtr, float[] samples);
    private native String transcribeFile(long nativePtr, String waveFile);
}