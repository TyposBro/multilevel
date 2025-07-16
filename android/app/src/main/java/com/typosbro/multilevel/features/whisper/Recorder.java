package com.typosbro.multilevel.features.whisper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class Recorder {

    private static final String TAG = "Recorder";
    private final Context mContext;
    private final AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private final RecorderListener mListener;
    private Thread recordingThread;

    // ADDED: For saving audio to a file
    private WavFileWriter wavFileWriter;

    public Recorder(Context context, RecorderListener listener) {
        this.mContext = context;
        this.mListener = listener;
    }

    public boolean isRecording() {
        return mIsRecording.get();
    }

    // MODIFIED: This method now returns the File being written to.
    public File start() {
        if (mIsRecording.get()) {
            Log.w(TAG, "start() called but recording is already in progress.");
            return null;
        }

        // ADDED: Logic to create and start the WavFileWriter
        File audioFile = null;
        try {
            wavFileWriter = new WavFileWriter();
            audioFile = wavFileWriter.start(mContext);
            Log.i(TAG, "Recording will be saved to: " + audioFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize WavFileWriter", e);
            wavFileWriter = null; // Ensure it's null so we don't try to use it
        }

        // --- Start the recording thread ---
        mIsRecording.set(true);
        recordingThread = new Thread(this::recordAudio);
        recordingThread.setName("AudioRecordingThread"); // Good practice to name threads
        recordingThread.start();
        Log.i(TAG, "Recording thread started.");
        return audioFile;
    }

    public void stop() {
        if (!mIsRecording.get()) {
            Log.w(TAG, "stop() called but not currently recording.");
            return;
        }
        Log.i(TAG, "Stop recording requested.");
        mIsRecording.set(false);

        // ADDED: Stop the file writer
        if (wavFileWriter != null) {
            try {
                wavFileWriter.stop();
                Log.i(TAG, "WAV file writing stopped successfully.");
            } catch (IOException e) {
                Log.e(TAG, "Failed to stop WavFileWriter", e);
            } finally {
                wavFileWriter = null;
            }
        }
    }

    private void recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Aborting recording.");
            mIsRecording.set(false);
            if (mListener != null) mListener.onRecordingStopped();
            return;
        }

        Log.d(TAG, "Audio recording thread running.");
        AudioRecord audioRecord = null;
        try {
            int sampleRateInHz = 16000;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

            // ADDED: Log buffer size for debugging
            Log.d(TAG, "AudioRecord buffer size: " + bufferSizeInBytes + " bytes.");

            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
            audioRecord.startRecording();

            Log.i(TAG, "AudioRecord started successfully.");

            byte[] audioData = new byte[bufferSizeInBytes];

            while (mIsRecording.get()) {
                int bytesRead = audioRecord.read(audioData, 0, audioData.length);
                if (bytesRead > 0) {
                    // Pass float data to ViewModel for live transcription
                    if (mListener != null) {
                        float[] samples = convertToFloatArray(ByteBuffer.wrap(audioData, 0, bytesRead));
                        mListener.onDataReceived(samples);
                    }
                    // Write byte data to file
                    if (wavFileWriter != null) {
                        wavFileWriter.writeData(audioData, bytesRead);
                    }
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: " + bytesRead);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during recording loop", e);
        } finally {
            Log.i(TAG, "Recording loop finished. Cleaning up.");
            mIsRecording.set(false); // Ensure state is correct
            if (audioRecord != null) {
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                    audioRecord.release();
                    Log.d(TAG, "AudioRecord stopped and released.");
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping AudioRecord", e);
                }
            }
            if (mListener != null) {
                Log.d(TAG, "Notifying listener that recording has stopped.");
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

    public interface RecorderListener {
        void onDataReceived(float[] samples);

        void onRecordingStopped();
    }
}