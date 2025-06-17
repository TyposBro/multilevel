#include <jni.h>
#include "TFLiteEngine.h" // Assuming this header file exists and is correct

extern "C" {

// JNI method to create an instance of TFLiteEngine
// CORRECTED: The function name now matches your package: com.typosbro.multilevel.features.whisper.engine
JNIEXPORT jlong JNICALL
Java_com_typosbro_multilevel_features_whisper_engine_WhisperEngineNative_createTFLiteEngine(JNIEnv *env, jobject thiz) {
    return reinterpret_cast<jlong>(new TFLiteEngine());
}

// JNI method to load the model
// CORRECTED: The function name now matches your package structure.
JNIEXPORT jint JNICALL
Java_com_typosbro_multilevel_features_whisper_engine_WhisperEngineNative_loadModel(JNIEnv *env, jobject thiz, jlong nativePtr, jstring modelPath, jboolean isMultilingual) {
    TFLiteEngine *engine = reinterpret_cast<TFLiteEngine *>(nativePtr);
    const char *cModelPath = env->GetStringUTFChars(modelPath, NULL);
    int result = engine->loadModel(cModelPath, isMultilingual);
    env->ReleaseStringUTFChars(modelPath, cModelPath);
    return static_cast<jint>(result);
}

// JNI method to free the model
// CORRECTED: The function name now matches your package structure.
JNIEXPORT void JNICALL
Java_com_typosbro_multilevel_features_whisper_engine_WhisperEngineNative_freeModel(JNIEnv *env, jobject thiz, jlong nativePtr) {
    TFLiteEngine *engine = reinterpret_cast<TFLiteEngine *>(nativePtr);
    engine->freeModel();
    delete engine;
}

// JNI method to transcribe audio buffer
// CORRECTED: The function name now matches your package structure.
JNIEXPORT jstring JNICALL
Java_com_typosbro_multilevel_features_whisper_engine_WhisperEngineNative_transcribeBuffer(JNIEnv *env, jobject thiz, jlong nativePtr, jfloatArray samples) {
    TFLiteEngine *engine = reinterpret_cast<TFLiteEngine *>(nativePtr);

    // Convert jfloatArray to std::vector<float>
    jsize len = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, 0);
    std::vector<float> sampleVector(data, data + len);
    env->ReleaseFloatArrayElements(samples, data, 0);

    std::string result = engine->transcribeBuffer(sampleVector);
    return env->NewStringUTF(result.c_str());
}

// JNI method to transcribe audio file
// CORRECTED: The function name now matches your package structure.
JNIEXPORT jstring JNICALL
Java_com_typosbro_multilevel_features_whisper_engine_WhisperEngineNative_transcribeFile(JNIEnv *env, jobject thiz, jlong nativePtr, jstring waveFile) {
    TFLiteEngine *engine = reinterpret_cast<TFLiteEngine *>(nativePtr);
    const char *cWaveFile = env->GetStringUTFChars(waveFile, NULL);
    std::string result = engine->transcribeFile(cWaveFile);
    env->ReleaseStringUTFChars(waveFile, cWaveFile);
    return env->NewStringUTF(result.c_str());
}

} // extern "C"