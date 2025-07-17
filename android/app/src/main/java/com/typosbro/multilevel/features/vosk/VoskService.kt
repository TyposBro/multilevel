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

@Singleton
class VoskService @Inject constructor(@ApplicationContext private val context: Context) {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognitionContinuation: Continuation<String>? = null

    companion object {
        private const val TAG = "VoskService"
        private const val VOSK_MODEL_ASSET_PATH =
            "model-en-us" // Adjust as needed
    }

    private val listener = object : RecognitionListener {
        override fun onResult(hypothesis: String?) {
            if (hypothesis.isNullOrBlank()) return
            try {
                val text = JSONObject(hypothesis).getString("text")
                if (text.isNotBlank()) {
                    Log.d(TAG, "onResult (segment): $text")
                    resultListener?.invoke(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse result json", e)
            }
        }

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

    /**
     * Starts a new recognition session.
     * This creates a fresh SpeechService to ensure no audio is carried over from previous states.
     * @return Boolean indicating if the service was started successfully.
     */
    fun startRecognition(): Boolean {
        if (model == null) {
            Log.e(TAG, "Model is not initialized.")
            return false
        }
        if (speechService != null) {
            Log.w(TAG, "Recognition already active. Stopping previous instance.")
            stopRecognition()
        }

        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(listener) // Starts un-paused
            Log.d(TAG, "Vosk recognition started.")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create/start SpeechService", e)
            speechService = null
            return false
        }
    }

    /**
     * Stops the current recognition session and shuts down the service.
     */
    fun stopRecognition() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        Log.d(TAG, "Vosk recognition stopped.")
    }

    /**
     * Releases all resources, including the Vosk model. Called when the ViewModel is destroyed.
     */
    fun release() {
        stopRecognition() // Ensure any active service is stopped.
        model?.close()
        model = null
        resultListener = null // Avoid potential context leaks
        Log.d(TAG, "Vosk service fully released.")
    }
}