package com.typosbro.multilevel.features.vosk

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.isActive
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// In typosbro-multilevel/android/app/src/main/java/com/typosbro/multilevel/features/vosk/VoskService.kt

@Singleton
class VoskService @Inject constructor(@ApplicationContext private val context: Context) {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var recognitionContinuation: Continuation<String>? = null

    companion object {
        private const val TAG = "VoskService"
        private const val VOSK_MODEL_ASSET_PATH =
            "model-en-us" // Adjust as needed
    }

    private val listener = object : RecognitionListener {
        // This is called when the recognizer detects a period of silence.
        override fun onResult(hypothesis: String?) {
            if (hypothesis.isNullOrBlank()) return
            try {
                val text = JSONObject(hypothesis).getString("text")
                if (text.isNotBlank()) {
                    Log.d(TAG, "onResult (segment): $text")
                    // Instead of resuming, we now pass the result to a callback.
                    // The ViewModel will handle appending it to the transcript.
                    resultListener?.invoke(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse result json", e)
            }
        }

        // This is called only when speechService.stop() is called.
        override fun onFinalResult(hypothesis: String?) {
            val text = try {
                if (hypothesis.isNullOrBlank()) "" else JSONObject(hypothesis).getString("text")
            } catch (e: Exception) {
                ""
            }
            Log.d(TAG, "onFinalResult: $text")
            if (recognitionContinuation?.context?.isActive == true) {
                recognitionContinuation?.resume(text)
                recognitionContinuation = null
            }
        }

        override fun onError(exception: Exception) {
            Log.e(TAG, "Recognition error", exception)
            if (recognitionContinuation?.context?.isActive == true) {
                recognitionContinuation?.resume("")
                recognitionContinuation = null
            }
        }

        override fun onPartialResult(hypothesis: String?) { /* We can show this on UI if needed */
        }

        override fun onTimeout() {}
    }

    // New callback to send intermediate results to the ViewModel
    var resultListener: ((String) -> Unit)? = null

    suspend fun initialize() = suspendCoroutine { continuation ->
        if (model != null) {
            continuation.resume(Unit)
            return@suspendCoroutine
        }
        StorageService.unpack(
            context, VOSK_MODEL_ASSET_PATH, "vosk-model",
            { unpackedModel ->
                model = unpackedModel
                continuation.resume(Unit)
            },
            { exception ->
                Log.e(TAG, "Failed to unpack model", exception)
            })
    }

    // Creates the service but does not start listening
    fun createAndStartService(): Boolean {
        if (model == null) {
            Log.e(TAG, "Model is not initialized.")
            return false
        }
        if (speechService != null) {
            return true // Already created
        }
        try {
            recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(listener)
            speechService?.setPause(true) // Start in a paused state
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create SpeechService", e)
            return false
        }
    }

    // Unpauses the recognizer to start processing audio
    fun startListening() {
        speechService?.setPause(false)
        Log.d(TAG, "Vosk listening resumed.")
    }

    // Pauses the recognizer, it will stop processing audio but not shut down.
    fun pauseListening() {
        speechService?.setPause(true)
        Log.d(TAG, "Vosk listening paused.")
    }

    // Fully stops and cleans up the service.
    fun stopAndRelease() {
        speechService?.stop()
        speechService?.shutdown()
        recognizer = null
        speechService = null
        resultListener = null
        Log.d(TAG, "Vosk service stopped and released.")
    }

    fun release() {
        stopAndRelease()
        model?.close()
        model = null
    }
}