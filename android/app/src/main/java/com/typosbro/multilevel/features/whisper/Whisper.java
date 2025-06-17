// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/features/whisper/Whisper.java
package com.typosbro.multilevel.features.whisper;

import android.content.Context;
import android.util.Log;

import com.typosbro.multilevel.features.whisper.engine.WhisperEngine;
import com.typosbro.multilevel.features.whisper.engine.WhisperEngineNative;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Whisper {

    public interface WhisperListener {
        void onUpdateReceived(String message);
        void onResultReceived(String result);
    }

    private static final String TAG = "Whisper";
    public static final String MSG_PROCESSING = "Processing...";
    public static final String MSG_PROCESSING_DONE = "Processing done...!";
    public static final String MSG_FILE_NOT_FOUND = "Input file doesn't exist..!";

    private final AtomicBoolean mInProgress = new AtomicBoolean(false);
    private final Queue<float[]> audioBufferQueue = new LinkedList<>();

    private final WhisperEngine mWhisperEngine;
    private WhisperListener mUpdateListener;

    public Whisper(Context context) {
        this.mWhisperEngine = new WhisperEngineNative(context);

        // Start thread for buffer transcription for live mic feed transcription
        Thread threadTranscbBuffer = new Thread(this::transcribeBufferLoop);
        threadTranscbBuffer.start();
    }

    public void setListener(WhisperListener listener) {
        this.mUpdateListener = listener;
    }

    public void loadModel(File modelPath, File vocabPath, boolean isMultilingual) {
        try {
            mWhisperEngine.initialize(modelPath.getAbsolutePath(), vocabPath.getAbsolutePath(), isMultilingual);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing model...", e);
            sendUpdate("Model initialization failed");
        }
    }

    public void unloadModel() {
        mWhisperEngine.deinitialize();
    }

    private void transcribeBufferLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            float[] samples = readBuffer();
            if (samples != null) {
                synchronized (mWhisperEngine) {
                    if (mWhisperEngine.isInitialized()) {
                        String result = mWhisperEngine.transcribeBuffer(samples);
                        sendResult(result);
                    }
                }
            }
        }
    }

    public void writeBuffer(float[] samples) {
        synchronized (audioBufferQueue) {
            audioBufferQueue.add(samples);
            audioBufferQueue.notify();
        }
    }

    private float[] readBuffer() {
        synchronized (audioBufferQueue) {
            while (audioBufferQueue.isEmpty()) {
                try {
                    audioBufferQueue.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return audioBufferQueue.poll();
        }
    }

    private void sendUpdate(String message) {
        if (mUpdateListener != null) {
            mUpdateListener.onUpdateReceived(message);
        }
    }

    private void sendResult(String message) {
        if (mUpdateListener != null) {
            mUpdateListener.onResultReceived(message);
        }
    }
}