package com.typosbro.multilevel.features.vosk

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import java.io.IOException
import java.io.InputStream

/**
 * Manager class to handle Vosk speech recognition functionality
 */
class VoskRecognitionManager(
    private val context: Context,
    private val model: Model,
    private val listener: RecognitionListener
) {
    private var speechService: SpeechService? = null
    private var speechStreamService: SpeechStreamService? = null

    /**
     * Start recognition from microphone
     */
    fun startMicrophoneRecognition() {
        if (speechService != null) {
            stopRecognition()
            return
        }

        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(listener)
        } catch (e: IOException) {
            listener.onError(e)
        }
    }

    /**
     * Start recognition from audio file in assets
     */
    fun startFileRecognition(fileName: String = "10001-90210-01803.wav") {
        if (speechStreamService != null) {
            stopRecognition()
            return
        }

        try {
            // Example of using grammar constraints like in the original code
            val recognizer = Recognizer(model, 16000.0f, "[\"one zero zero zero one\", " +
                    "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]")

            val assetManager = context.assets
            val ais: InputStream = assetManager.open(fileName)

            // Skip WAV header
            if (ais.skip(44) != 44L) throw IOException("File too short")

            speechStreamService = SpeechStreamService(recognizer, ais, 16000f)
            speechStreamService?.start(listener)
        } catch (e: IOException) {
            listener.onError(e)
        }
    }

    /**
     * Check if microphone recognition is active
     * @return true if microphone recognition is active, false otherwise
     */
    fun isMicrophoneActive(): Boolean {
        return speechService != null
    }

    /**
     * Stop current recognition (mic or file)
     */
    fun stopRecognition() {
        if (speechService != null) {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
        }

        if (speechStreamService != null) {
            speechStreamService?.stop()
            speechStreamService = null
        }
    }

    /**
     * Set pause state for mic recognition
     */
    fun setPause(paused: Boolean) {
        speechService?.setPause(paused)
    }
}