// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/features/whisper/Recorder.java
package com.typosbro.multilevel.features.whisper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class Recorder {

    public interface RecorderListener {
        void onUpdateReceived(String message);
        void onDataReceived(float[] samples);
    }

    private static final String TAG = "Recorder";
    public static final String MSG_RECORDING = "Recording...";
    public static final String MSG_RECORDING_DONE = "Recording done...!";
    public static final int RECORDING_DURATION_S = 60;

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);
    private RecorderListener mListener;
    private Thread recordingThread;

    public Recorder(Context context) {
        this.mContext = context;
    }

    public void setListener(RecorderListener listener) {
        this.mListener = listener;
    }

    public void start() {
        if (mInProgress.get()) {
            Log.d(TAG, "Recording is already in progress...");
            return;
        }
        mInProgress.set(true);
        recordingThread = new Thread(this::recordAudio);
        recordingThread.start();
    }

    public void stop() {
        if (!mInProgress.get()) return;
        mInProgress.set(false);
        if (recordingThread != null && recordingThread.isAlive()) {
            try {
                recordingThread.join(500); // Wait a bit for the loop to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sendUpdate(String message) {
        if (mListener != null)
            mListener.onUpdateReceived(message);
    }

    private void sendData(float[] samples) {
        if (mListener != null)
            mListener.onDataReceived(samples);
    }

    private void recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sendUpdate("Permission not granted for recording");
            mInProgress.set(false);
            return;
        }

        sendUpdate(MSG_RECORDING);

        int sampleRateInHz = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
        audioRecord.startRecording();

        // Process audio in chunks of ~1 second for real-time feel
        int chunkBufferSize = sampleRateInHz * (16 / 8) * 1; // 1 sec of 16-bit mono audio
        byte[] audioData = new byte[chunkBufferSize];
        long recordingStartTime = System.currentTimeMillis();

        while (mInProgress.get()) {
            int bytesRead = audioRecord.read(audioData, 0, audioData.length);
            if (bytesRead > 0) {
                float[] samples = convertToFloatArray(ByteBuffer.wrap(audioData, 0, bytesRead));
                sendData(samples);
            } else {
                Log.e(TAG, "AudioRecord read error: " + bytesRead);
                break;
            }

            // Optional: Add a max duration check
            if (System.currentTimeMillis() - recordingStartTime > RECORDING_DURATION_S * 1000) {
                Log.d(TAG, "Max recording duration reached.");
                break;
            }
        }

        audioRecord.stop();
        audioRecord.release();
        mInProgress.set(false);
        sendUpdate(MSG_RECORDING_DONE);
    }

    private float[] convertToFloatArray(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] samples = new float[buffer.remaining() / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort() / 32768.0f;
        }
        return samples;
    }
}