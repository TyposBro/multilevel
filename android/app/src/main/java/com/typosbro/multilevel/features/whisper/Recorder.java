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

    // A simpler listener that only provides audio data.
    public interface RecorderListener {
        void onDataReceived(float[] samples);
        void onRecordingStopped(); // Add a callback for when recording actually stops
    }

    private static final String TAG = "Recorder";
    private final Context mContext;
    private final AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private RecorderListener mListener;
    private Thread recordingThread;

    public Recorder(Context context, RecorderListener listener) {
        this.mContext = context;
        this.mListener = listener;
    }

    public boolean isRecording() {
        return mIsRecording.get();
    }

    public void start() {
        if (mIsRecording.get()) {
            Log.d(TAG, "Recording is already in progress...");
            return;
        }
        mIsRecording.set(true);
        recordingThread = new Thread(this::recordAudio);
        recordingThread.start();
    }

    public void stop() {
        if (!mIsRecording.get()) return;
        mIsRecording.set(false); // Signal the thread to stop
    }

    private void recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission not granted for recording");
            mIsRecording.set(false);
            if (mListener != null) mListener.onRecordingStopped();
            return;
        }

        Log.d(TAG, "Starting audio recording thread.");
        int sampleRateInHz = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);

        try {
            audioRecord.startRecording();
            byte[] audioData = new byte[bufferSizeInBytes];

            while (mIsRecording.get()) {
                int bytesRead = audioRecord.read(audioData, 0, audioData.length);
                if (bytesRead > 0 && mListener != null) {
                    float[] samples = convertToFloatArray(ByteBuffer.wrap(audioData, 0, bytesRead));
                    mListener.onDataReceived(samples);
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: " + bytesRead);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during recording", e);
        } finally {
            Log.d(TAG, "Stopping audio recording thread.");
            mIsRecording.set(false); // Ensure state is false
            audioRecord.stop();
            audioRecord.release();
            if (mListener != null) {
                mListener.onRecordingStopped();
            }
        }
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