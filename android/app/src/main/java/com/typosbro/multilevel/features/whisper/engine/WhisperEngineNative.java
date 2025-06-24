// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/features/whisper/engine/WhisperEngineNative.java

package com.typosbro.multilevel.features.whisper.engine;

import android.content.Context;
import android.util.Log;

public class WhisperEngineNative implements WhisperEngine {
    static {
        // This must match the 'target_name' in the native code's CMakeLists.txt
        System.loadLibrary("audioEngine");
    }

    private final String TAG = "WhisperEngineNative";
    private long nativePtr; // Make it non-final to allow proper release
    private boolean mIsInitialized = false;

    public WhisperEngineNative(Context context) {
        // The native pointer is initialized here.
        // It's crucial to release it later to avoid memory leaks.
        nativePtr = createTFLiteEngine();
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) {
        if (nativePtr == 0) {
            Log.e(TAG, "Cannot initialize, native pointer is null.");
            return false;
        }
        int ret = loadModel(modelPath, multilingual);
        Log.d(TAG, "Native model loaded: " + modelPath + ", result: " + ret);
        mIsInitialized = (ret == 0);
        return mIsInitialized;
    }

    @Override
    public void deinitialize() {
        if (nativePtr != 0) {
            freeModel();
            // After freeing the native resources, destroy the engine itself
            nativePtr = 0; // Set to 0 to prevent reuse
        }
        mIsInitialized = false;
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        if (!mIsInitialized || nativePtr == 0) {
            Log.w(TAG, "transcribeBuffer called before engine was initialized or after it was released.");
            return "";
        }

        // --- THE FIX IS HERE ---
        // Wrap the native call in a try-catch block for any Throwable.
        try {
            // This is the call that is causing the native crash.
            String result = transcribeBuffer(nativePtr, samples);

            // It's also good practice to check for a null result from JNI.
            if (result == null) {
                Log.w(TAG, "Native transcribeBuffer returned null. Returning empty string.");
                return "";
            }
            return result;

        } catch (Throwable t) {
            // Catch any error, including the JNI UTF-8 error, which is a Throwable.
            Log.e(TAG, "FATAL: Native 'transcribeBuffer' call failed and was caught.", t);

            // Return a safe, empty string to prevent the app from crashing.
            return "";
        }
    }

    @Override
    public String transcribeFile(String waveFile) {
        if (!mIsInitialized || nativePtr == 0) {
            Log.w(TAG, "transcribeFile called before engine was initialized or after it was released.");
            return "";
        }

        // --- ALSO APPLY THE FIX HERE FOR CONSISTENCY ---
        try {
            String result = transcribeFile(nativePtr, waveFile);
            if (result == null) {
                Log.w(TAG, "Native transcribeFile returned null. Returning empty string.");
                return "";
            }
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "FATAL: Native 'transcribeFile' call failed and was caught.", t);
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