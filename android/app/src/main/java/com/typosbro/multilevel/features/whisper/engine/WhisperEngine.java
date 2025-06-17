// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/features/whisper/engine/WhisperEngine.java
package com.typosbro.multilevel.features.whisper.engine;

import java.io.IOException;

public interface WhisperEngine {
    boolean isInitialized();
    boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException;
    void deinitialize();
    String transcribeFile(String wavePath);
    String transcribeBuffer(float[] samples);
}