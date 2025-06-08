package com.typosbro.multilevel.util // Or a suitable package

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.typosbro.multilevel.features.inference.StyleLoader
import java.io.File
import java.io.FileOutputStream


object AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(context: Context, audioBytes: ByteArray, onCompletion: (() -> Unit)? = null) {
        stopPlayback() // Stop any previous playback

        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("audio_bytes_", ".wav", context.cacheDir)
            FileOutputStream(tempFile).use { fos -> fos.write(audioBytes) }
            Log.d("AudioPlayer", "Temporary file created from bytes: ${tempFile.absolutePath}")
            prepareAndPlay(context, Uri.fromFile(tempFile), onCompletion, tempFile)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio from bytes", e)
            stopPlayback()
            tempFile?.delete()
        }
    }

    fun playAudioFromBase64(context: Context, base64String: String, onCompletion: (() -> Unit)? = null) {
        stopPlayback()
        var tempFile: File? = null
        try {
            // 1. Decode Base64 to byte array
            val audioBytes = Base64.decode(base64String, Base64.DEFAULT)

            // 2. Write bytes to a temporary file
            tempFile = File.createTempFile("audio_b64_", ".wav", context.cacheDir) // Assume WAV for now
            FileOutputStream(tempFile).use { fos -> fos.write(audioBytes) }
            Log.d("AudioPlayer", "Temporary file created from Base64: ${tempFile.absolutePath}")

            // 3. Prepare and Play
            prepareAndPlay(context, Uri.fromFile(tempFile), onCompletion, tempFile)

        } catch (e: IllegalArgumentException) {
            Log.e("AudioPlayer", "Error decoding Base64 string", e)
            stopPlayback() // Clean up MediaPlayer if initialization started
            tempFile?.delete() // Clean up temp file
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error preparing or playing audio from Base64", e)
            stopPlayback()
            tempFile?.delete()
        }
    }

    // Helper function to setup and play MediaPlayer
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
                stopPlayback() // Calls release internally
                tempFileToDelete?.delete() // Delete temp file after playback
                Log.d("AudioPlayer", "Temp file deleted: ${tempFileToDelete?.name}")
                onCompletion?.invoke()
            }
            setOnErrorListener { mp, what, extra ->
                Log.e("AudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                stopPlayback() // Calls release internally
                tempFileToDelete?.delete() // Delete temp file on error
                Log.d("AudioPlayer", "Temp file deleted on error: ${tempFileToDelete?.name}")
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
        tokens: LongArray,
        voice: String,
        speed: Float,
        session: OrtSession,
        context: Context,
    ): Pair<FloatArray, Int> {
        val MAX_PHONEME_LENGTH = 400
        val SAMPLE_RATE = 22050

        if (tokens.size > MAX_PHONEME_LENGTH) {
            throw IllegalArgumentException("Context length is $MAX_PHONEME_LENGTH, but leave room for the pad token 0 at the start & end")
        }

        val styleLoader = StyleLoader(context)
        val styleIndex = tokens.size
        val styleArray = styleLoader.getStyleArray(name = voice, index = styleIndex)

        val tokenTensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            arrayOf(tokens)
        )
        val styleTensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            styleArray
        )
        val speedTensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            floatArrayOf(speed)
        )

        val inputs = mapOf(
            "input_ids" to tokenTensor,
            "style" to styleTensor,
            "speed" to speedTensor
        )
        val results = session.run(inputs)
        val audioTensor = (results[0].value as Array<FloatArray>)[0]
        results.close()

        return Pair(audioTensor, SAMPLE_RATE)
    }

}