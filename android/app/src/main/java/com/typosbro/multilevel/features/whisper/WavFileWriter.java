package com.typosbro.multilevel.features.whisper;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WavFileWriter {
    private static final String TAG = "WavFileWriter";
    private static final int RECORDER_SAMPLE_RATE = 16000;
    private static final int RECORDER_CHANNELS = 1; // Mono
    private static final int RECORDER_BPP = 16; // Bits per sample

    private File file;
    private RandomAccessFile randomAccessFile;
    private int payloadSize = 0;

    public File start(Context context) throws IOException {
        File dir = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS);
        }
        if (dir == null) {
            throw new IOException("Cannot get external files directory.");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        file = new File(dir, "recording_" + timestamp + ".wav");

        randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.setLength(0); // Clear file
        randomAccessFile.seek(0);
        randomAccessFile.write(new byte[44]); // Write a dummy 44-byte header

        Log.i(TAG, "Started writing to WAV file: " + file.getAbsolutePath());
        return file;
    }

    public void writeData(byte[] data, int bytesRead) throws IOException {
        if (randomAccessFile == null) return;
        randomAccessFile.write(data, 0, bytesRead);
        payloadSize += bytesRead;
    }

    public void stop() throws IOException {
        if (randomAccessFile == null) return;

        randomAccessFile.seek(0); // Go to the beginning of the file
        writeWavHeader();
        randomAccessFile.close();
        randomAccessFile = null;

        Log.i(TAG, "Stopped writing WAV file. Final size: " + payloadSize + " bytes.");
    }

    private void writeWavHeader() throws IOException {
        long totalDataLen = payloadSize;
        long totalAudioLen = totalDataLen + 36;
        long longSampleRate = RECORDER_SAMPLE_RATE;
        int channels = RECORDER_CHANNELS;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLE_RATE * channels / 8;

        byte[] header = new byte[44];
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalAudioLen & 0xff);
        header[5] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[6] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[7] = (byte) ((totalAudioLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * RECORDER_BPP / 8);
        header[33] = 0;
        header[34] = RECORDER_BPP;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalDataLen & 0xff);
        header[41] = (byte) ((totalDataLen >> 8) & 0xff);
        header[42] = (byte) ((totalDataLen >> 16) & 0xff);
        header[43] = (byte) ((totalDataLen >> 24) & 0xff);

        randomAccessFile.write(header, 0, 44);
    }
}