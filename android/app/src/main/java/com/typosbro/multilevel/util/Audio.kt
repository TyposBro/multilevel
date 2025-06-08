package com.typosbro.multilevel.util // Or a suitable package

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64 // Keep for playAudioFromBase64 if still used elsewhere
import android.util.Log
import com.typosbro.multilevel.features.inference.StyleLoader
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null

    // Function to convert FloatArray from model to WAV ByteArray
    fun convertFloatArrayToWavByteArray(audioData: FloatArray, sampleRate: Int): ByteArray {
        // Create WAV header using the existing private function
        val header = createWavHeader(audioData.size, sampleRate) // audioData.size is number of samples

        // Convert float samples to 16-bit PCM
        val pcmByteBuffer = ByteBuffer.allocate(audioData.size * 2) // Each float sample becomes a 2-byte short
        pcmByteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = pcmByteBuffer.asShortBuffer()

        for (sample in audioData) {
            val clampedSample = sample.coerceIn(-1.0f, 1.0f) // Ensure range
            val pcmValue = (clampedSample * Short.MAX_VALUE).toInt().toShort()
            shortBuffer.put(pcmValue)
        }

        // Combine header and PCM data
        val wavBytes = ByteArray(header.size + pcmByteBuffer.array().size)
        System.arraycopy(header, 0, wavBytes, 0, header.size)
        System.arraycopy(pcmByteBuffer.array(), 0, wavBytes, header.size, pcmByteBuffer.array().size)

        return wavBytes
    }

    // This is the original createWavHeader from your provided code.
    private fun createWavHeader(dataSize: Int, sampleRate: Int): ByteArray {
        val header = ByteArray(44)
        // totalDataSize here refers to (PCM data size in bytes) + (size of header fields excluding 'RIFF' and chunk size itself)
        // which is (dataSize * 2) + 36
        val totalDataSize = dataSize * 2 + 36 // dataSize is number of samples
        val numChannels = 1 // Mono
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val byteRate = sampleRate * numChannels * bytesPerSample


        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte() // RIFF
        header[4] = (totalDataSize and 0xff).toByte() // ChunkSize = totalDataSize
        header[5] = (totalDataSize shr 8 and 0xff).toByte()
        header[6] = (totalDataSize shr 16 and 0xff).toByte()
        header[7] = (totalDataSize shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte() // WAVE
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte() // 'fmt ' chunk
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Subchunk1Size (16 for PCM)
        header[20] = 1; header[21] = 0 // AudioFormat (1 for PCM)
        header[22] = numChannels.toByte(); header[23] = 0 // NumChannels (1 for mono)
        header[24] = (sampleRate and 0xff).toByte() // SampleRate
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte() // ByteRate
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (numChannels * bytesPerSample).toByte(); header[33] = 0 // BlockAlign
        header[34] = bitsPerSample.toByte(); header[35] = 0 // BitsPerSample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte() // 'data' chunk
        val pcmDataSize = dataSize * bytesPerSample // Actual PCM data size in bytes
        header[40] = (pcmDataSize and 0xff).toByte() // Subchunk2Size (PCM data size)
        header[41] = (pcmDataSize shr 8 and 0xff).toByte()
        header[42] = (pcmDataSize shr 16 and 0xff).toByte()
        header[43] = (pcmDataSize shr 24 and 0xff).toByte()
        return header
    }

    fun playAudio(context: Context, audioBytes: ByteArray, onCompletion: (() -> Unit)? = null) {
        stopPlayback() // Stop any previous playback

        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("audio_tts_", ".wav", context.cacheDir)
            FileOutputStream(tempFile).use { fos -> fos.write(audioBytes) }
            Log.d("AudioPlayer", "Temporary TTS audio file created: ${tempFile.absolutePath}")
            prepareAndPlay(context, Uri.fromFile(tempFile), onCompletion, tempFile)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio from generated bytes", e)
            stopPlayback()
            tempFile?.delete()
            onCompletion?.invoke() // Ensure completion is called on error too
        }
    }

    @Deprecated("Use client-side TTS with input_ids instead of Base64 audio from backend.")
    fun playAudioFromBase64(context: Context, base64String: String, onCompletion: (() -> Unit)? = null) {
        stopPlayback()
        var tempFile: File? = null
        try {
            val audioBytes = Base64.decode(base64String, Base64.DEFAULT)
            tempFile = File.createTempFile("audio_b64_", ".wav", context.cacheDir)
            FileOutputStream(tempFile).use { fos -> fos.write(audioBytes) }
            Log.d("AudioPlayer", "Temporary file created from Base64: ${tempFile.absolutePath}")
            prepareAndPlay(context, Uri.fromFile(tempFile), onCompletion, tempFile)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio from Base64", e)
            stopPlayback()
            tempFile?.delete()
            onCompletion?.invoke()
        }
    }

    private fun prepareAndPlay(context: Context, uri: Uri, onCompletion: (() -> Unit)?, tempFileToDelete: File?) {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(context, uri)
            setOnPreparedListener { mp ->
                Log.d("AudioPlayer", "MediaPlayer prepared, starting playback.")
                mp.start()
            }
            setOnCompletionListener { mp ->
                Log.d("AudioPlayer", "MediaPlayer playback completed.")
                stopPlayback()
                tempFileToDelete?.delete()
                Log.d("AudioPlayer", "Temp file deleted: ${tempFileToDelete?.name}")
                onCompletion?.invoke()
            }
            setOnErrorListener { mp, what, extra ->
                Log.e("AudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                stopPlayback()
                tempFileToDelete?.delete()
                Log.d("AudioPlayer", "Temp file deleted on error: ${tempFileToDelete?.name}")
                onCompletion?.invoke() // Call completion on error too
                true // Error handled
            }
            prepareAsync()
        }
    }

    fun stopPlayback() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) { it.stop() }
                it.reset()
                it.release()
                Log.d("AudioPlayer", "MediaPlayer stopped and released.")
            } catch (e: Exception) { Log.w("AudioPlayer", "Error stopping/releasing MediaPlayer: ${e.message}") }
        }
        mediaPlayer = null
    }

    fun release() {
        stopPlayback()
    }

    fun createAudio(
        tokens: LongArray, // These are the input_ids
        voice: String,     // e.g., "bf_emma"
        speed: Float,
        session: OrtSession,
        context: Context,
    ): Pair<FloatArray, Int> {
        val MAX_INPUT_ID_LENGTH = 400 // Max sequence length your model can handle
        val SAMPLE_RATE = 22050      // Sample rate your model outputs

        if (tokens.size > MAX_INPUT_ID_LENGTH) {
            Log.w("AudioPlayer", "Input tokens length (${tokens.size}) is greater than MAX_INPUT_ID_LENGTH ($MAX_INPUT_ID_LENGTH). This might lead to issues or truncation by the model.")
            // Consider truncating tokens here if necessary: tokens.take(MAX_INPUT_ID_LENGTH).toLongArray()
        }
        if (tokens.isEmpty()) {
            throw IllegalArgumentException("Input tokens array cannot be empty.")
        }

        val styleLoader = StyleLoader(context)
        // Assuming index 0 is the default/primary style vector for the given voice name.
        // If your .raw style files contain multiple styles, you might need a different logic for 'index'.
        val styleArray = styleLoader.getStyleArray(name = voice, index = 0)

        val tokenTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), arrayOf(tokens)) // Shape: [1, sequence_length]
        val styleTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), styleArray)     // Shape: [1, style_vector_dim] e.g. [1, 256]
        val speedTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), floatArrayOf(speed)) // Shape: [1]

        // Verify these input names match your ONNX model's expected input names
        val inputs = mapOf(
            "input_ids" to tokenTensor,
            "style" to styleTensor, // Or "style_embedding", "speaker_embedding" etc.
            "speed" to speedTensor
        )
        Log.d("AudioPlayer", "Running ONNX inference. Input shapes: input_ids=${tokenTensor.info.shape.contentToString()}, style=${styleTensor.info.shape.contentToString()}, speed=${speedTensor.info.shape.contentToString()}")

        try {
            val results = session.run(inputs)
            // Assuming the first output tensor is the audio waveform
            val audioOutputTensor = results[0]
            val audioFloatArray = when (val value = audioOutputTensor.value) {
                is Array<*> -> (value as Array<FloatArray>)[0] // If model outputs shape [1, num_samples]
                is FloatArray -> value                         // If model outputs shape [num_samples]
                else -> throw IllegalStateException("Unexpected audio tensor output type: ${value?.javaClass?.name}")
            }
            Log.d("AudioPlayer", "Inference successful. Audio data length: ${audioFloatArray.size}")
            results.close() // IMPORTANT: Close results to free native resources
            return Pair(audioFloatArray, SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "ONNX inference error: ${e.message}", e)
            throw e // Re-throw to be handled by the ViewModel
        } finally {
            // Clean up tensors
            tokenTensor.close()
            styleTensor.close()
            speedTensor.close()
        }
    }
}