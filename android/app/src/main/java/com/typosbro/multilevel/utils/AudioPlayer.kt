package com.typosbro.multilevel.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import com.typosbro.multilevel.features.inference.StyleLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AudioPlayer {

    private var currentMediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null

    // --- NEW ROBUST SUSPENDABLE PLAYBACK FUNCTIONS ---

    /**
     * Plays audio from a raw resource and suspends the coroutine until playback is complete or fails.
     * This version is robust against race conditions using a try/finally block for cleanup.
     */
    suspend fun playFromRawAndWait(context: Context, @RawRes rawResId: Int) {
        stopCurrentSound() // Stop any sound that might be playing.

        val player = MediaPlayer()
        currentMediaPlayer = player // Assign so it can be stopped externally if needed.

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")

                continuation.invokeOnCancellation {
                    Log.d(
                        "AudioPlayer",
                        "SUSPEND: Coroutine cancelled for raw $rawResId. The 'finally' block will handle cleanup."
                    )
                }

                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA).build()
                    )
                    player.setDataSource(context, uri)
                    player.setOnPreparedListener { it.start() }
                    player.setOnCompletionListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    player.setOnErrorListener { _, what, extra ->
                        val error =
                            RuntimeException("MediaPlayer error for raw $rawResId: what=$what, extra=$extra")
                        if (continuation.isActive) continuation.resumeWithException(error)
                        true
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } finally {
            // This is guaranteed to run once the coroutine is done, preventing race conditions.
            cleanupPlayerInstance(player, "playFromRawAndWait finally block")
        }
    }

    /**
     * Plays audio from a URL and suspends the coroutine until playback is complete or fails.
     * This version is robust against race conditions using a try/finally block for cleanup.
     */
    suspend fun playFromUrlAndWait(context: Context, url: String) {
        stopCurrentSound()

        val player = MediaPlayer()
        currentMediaPlayer = player

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation {
                    Log.d(
                        "AudioPlayer",
                        "SUSPEND: Coroutine cancelled for URL $url. The 'finally' block will handle cleanup."
                    )
                }

                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA).build()
                    )
                    player.setDataSource(url)
                    player.setOnPreparedListener { it.start() }
                    player.setOnCompletionListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    player.setOnErrorListener { _, what, extra ->
                        val error =
                            RuntimeException("MediaPlayer error for URL $url: what=$what, extra=$extra")
                        if (continuation.isActive) continuation.resumeWithException(error)
                        true
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } finally {
            cleanupPlayerInstance(player, "playFromUrlAndWait finally block")
        }
    }

    // --- Original Functions (kept for compatibility) ---

    fun playFromUrl(context: Context, url: String, onCompletionCallback: () -> Unit) {
        Log.d("AudioPlayer", "Attempting to play from URL: $url")
        stopCurrentSound()
        val player = MediaPlayer()
        currentMediaPlayer = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            player.setDataSource(url)
            player.setOnPreparedListener {
                it.start()
            }
            player.setOnCompletionListener {
                onCompletionCallback.invoke()
                cleanupPlayerInstance(it, "URL playback (non-suspend)")
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e("AudioPlayer", "MediaPlayer error for URL ($url): what=$what, extra=$extra")
                onCompletionCallback.invoke()
                cleanupPlayerInstance(mp, "URL error (non-suspend)")
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Exception setting up MediaPlayer for URL ($url)", e)
            onCompletionCallback.invoke()
            cleanupPlayerInstance(player, "URL setup exception (non-suspend)")
        }
    }

    fun playFromRaw(context: Context, @RawRes rawResId: Int, onCompletionCallback: () -> Unit) {
        Log.d("AudioPlayer", "Attempting to play from raw resource ID: $rawResId")
        stopCurrentSound()
        val player = MediaPlayer()
        currentMediaPlayer = player
        val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            player.setDataSource(context, uri)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                onCompletionCallback.invoke()
                cleanupPlayerInstance(it, "Raw playback (non-suspend)")
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e(
                    "AudioPlayer",
                    "MediaPlayer error for raw resource ($rawResId): what=$what, extra=$extra"
                )
                onCompletionCallback.invoke()
                cleanupPlayerInstance(mp, "Raw error (non-suspend)")
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Exception setting up MediaPlayer for raw resource ($rawResId)", e)
            onCompletionCallback.invoke()
            cleanupPlayerInstance(player, "Raw setup exception (non-suspend)")
        }
    }

    /**
     * The main function to play audio from a byte array. It writes the bytes to a temporary
     * file and then plays it using MediaPlayer.
     */
    fun playAudio(context: Context, audioBytes: ByteArray, onCompletion: (() -> Unit)? = null) {
        Log.d("AudioPlayer", "Attempting to play audio from byte array.")
        stopCurrentSound()

        val player = MediaPlayer()
        currentMediaPlayer = player

        try {
            tempAudioFile = File.createTempFile("audio_tts_", ".wav", context.cacheDir).apply {
                FileOutputStream(this).use { fos -> fos.write(audioBytes) }
                deleteOnExit()
            }
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            player.setDataSource(tempAudioFile!!.absolutePath)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                onCompletion?.invoke()
                cleanupPlayerInstance(it, "TTS playback")
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e("AudioPlayer", "MediaPlayer error for TTS audio: what=$what, extra=$extra")
                onCompletion?.invoke()
                cleanupPlayerInstance(mp, "TTS error")
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio from generated bytes", e)
            onCompletion?.invoke()
            cleanupPlayerInstance(player, "TTS setup exception")
        }
    }

    // --- Cleanup and Helper Logic ---

    private fun cleanupPlayerInstance(player: MediaPlayer, reason: String) {
        Log.d("AudioPlayer", "Cleaning up MediaPlayer instance due to: $reason")
        try {
            // A simple way to check if the player is still valid is to catch the IllegalStateException.
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        } catch (e: IllegalStateException) {
            // This is expected if the player was already released, so we can log it as a warning.
            Log.w(
                "AudioPlayer",
                "Player was already released or in a bad state. Reason: ${e.message}"
            )
        } catch (e: Exception) {
            Log.w("AudioPlayer", "Generic exception during cleanup: ${e.message}")
        }

        // Only nullify the global variable if it's the one we're cleaning up.
        if (currentMediaPlayer == player) {
            currentMediaPlayer = null
        }

        // Clean up temp file if this was a TTS playback
        if (reason.contains("TTS")) {
            tempAudioFile?.delete()
            tempAudioFile = null
        }
    }

    private fun stopCurrentSound() {
        currentMediaPlayer?.let { playerToStop ->
            Log.d("AudioPlayer", "stopCurrentSound called. Stopping player.")
            cleanupPlayerInstance(playerToStop, "stopCurrentSound explicit call")
        }
    }

    fun release() {
        Log.d("AudioPlayer", "AudioPlayer.release() called.")
        stopCurrentSound()
    }


    // --- ONNX and WAV Generation Logic (Unchanged from original) ---

    suspend fun playFromInputIds(inputIds: List<Long>, context: Context, onCompletion: () -> Unit) {
        stopCurrentSound()
        try {
            val wavBytes = createAudioAndConvertToWav(inputIds, context)
            playAudio(context, wavBytes, onCompletion)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error generating or playing audio from input IDs", e)
            onCompletion.invoke()
        }
    }

    suspend fun createAudioAndConvertToWav(inputIds: List<Long>, context: Context): ByteArray {
        return withContext(Dispatchers.IO) {
            val session = OnnxRuntimeManager.getSession()
            val (audioFloatArray, sampleRate) = createAudio(
                tokens = inputIds.toLongArray(),
                voice = "bf_alice",
                speed = 0.7f,
                session = session,
                context = context
            )
            convertFloatArrayToWavByteArray(audioFloatArray, sampleRate)
        }
    }

    private fun convertFloatArrayToWavByteArray(audioData: FloatArray, sampleRate: Int): ByteArray {
        val header = createWavHeader(audioData.size, sampleRate)
        val pcmByteBuffer = ByteBuffer.allocate(audioData.size * 2).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            asShortBuffer().let { shortBuffer ->
                for (sample in audioData) {
                    val clampedSample = sample.coerceIn(-1.0f, 1.0f)
                    val pcmValue = (clampedSample * Short.MAX_VALUE).toInt().toShort()
                    shortBuffer.put(pcmValue)
                }
            }
        }
        return ByteArray(header.size + pcmByteBuffer.array().size).also { wavBytes ->
            System.arraycopy(header, 0, wavBytes, 0, header.size)
            System.arraycopy(
                pcmByteBuffer.array(),
                0,
                wavBytes,
                header.size,
                pcmByteBuffer.array().size
            )
        }
    }

    fun createAudio(
        tokens: LongArray, voice: String, speed: Float, session: OrtSession, context: Context
    ): Pair<FloatArray, Int> {
        val SAMPLE_RATE = 22050

        if (tokens.isEmpty()) throw IllegalArgumentException("Input tokens array cannot be empty.")

        val styleLoader = StyleLoader(context)
        val styleArray = styleLoader.getStyleArray(name = voice, index = 0)
        val tokenTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), arrayOf(tokens))
        val styleTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), styleArray)
        val speedTensor =
            OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), floatArrayOf(speed))

        val inputs = mapOf(
            "input_ids" to tokenTensor, "style" to styleTensor, "speed" to speedTensor
        )

        try {
            session.run(inputs).use { results ->
                val audioOutputTensor = results[0]
                val audioFloatArray = when (val value = audioOutputTensor.value) {
                    is Array<*> -> (value as Array<FloatArray>)[0]
                    is FloatArray -> value
                    else -> throw IllegalStateException("Unexpected audio tensor output type: ${value?.javaClass?.name}")
                }
                return Pair(audioFloatArray, SAMPLE_RATE)
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "ONNX inference error: ${e.message}", e)
            throw e
        } finally {
            tokenTensor.close()
            styleTensor.close()
            speedTensor.close()
        }
    }

    private fun createWavHeader(dataSize: Int, sampleRate: Int): ByteArray {
        val header = ByteArray(44)
        val totalDataSize = dataSize * 2 + 36
        val numChannels = 1
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val byteRate = sampleRate * numChannels * bytesPerSample

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] =
            'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataSize and 0xff).toByte()
        header[5] = (totalDataSize shr 8 and 0xff).toByte()
        header[6] = (totalDataSize shr 16 and 0xff).toByte()
        header[7] = (totalDataSize shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] =
            'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] =
            't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = numChannels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (numChannels * bytesPerSample).toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] =
            't'.code.toByte(); header[39] = 'a'.code.toByte()
        val pcmDataSize = dataSize * bytesPerSample
        header[40] = (pcmDataSize and 0xff).toByte()
        header[41] = (pcmDataSize shr 8 and 0xff).toByte()
        header[42] = (pcmDataSize shr 16 and 0xff).toByte()
        header[43] = (pcmDataSize shr 24 and 0xff).toByte()
        return header
    }
}