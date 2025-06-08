package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.features.vosk.VoskRecognitionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.StorageService
import com.typosbro.multilevel.data.repositories.Result
import com.typosbro.multilevel.ui.component.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.typosbro.multilevel.util.AudioPlayer

class ChatDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository
) : AndroidViewModel(application) {

    val chatId: String = savedStateHandle["chatId"] ?: error("Chat ID not found in navigation args")

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState = _uiState.asStateFlow()

    // Vosk related state
    private var voskManager: VoskRecognitionManager? = null
    private var voskModel: Model? = null
    private var recognitionResultsBuffer = "" // Buffer for full results between partials
    private var currentPartialText = "" // Current partial result - internal buffer

    // --- Timers ---
    private var silenceDetectionJob: Job? = null
    private var maxRecordingJob: Job? = null // Timer for overall duration
    private val SILENCE_DELAY_MS = 10000L // 10 seconds
    private val MAX_RECORDING_DURATION_S = 30 // Duration in seconds for display
    private val MAX_RECORDING_DURATION_MS = MAX_RECORDING_DURATION_S * 1000L // Duration in ms


    init {
        Log.d("ChatDetailViewModel", "Initializing for chatId: $chatId")
        loadChatHistory()
        initVoskModel()
    }

    // --- Vosk Methods ---
    private fun initVoskModel() {
        viewModelScope.launch {
            StorageService.unpack(getApplication(), "model-en-us", "model",
                { unpackedModel ->
                    voskModel = unpackedModel
                    voskModel?.let {
                        voskManager = VoskRecognitionManager(getApplication(), it, recognitionListener)
                        _uiState.update { state -> state.copy(isModelReady = true) }
                        Log.d("VoskInit", "Vosk Model unpacked and Manager created.")
                    } ?: run {
                        Log.e("VoskInit", "Model unpacked but is null.")
                        _uiState.update { state -> state.copy(error = "Failed to initialize recognition model.") }
                    }
                },
                { exception ->
                    Log.e("VoskInit", "Failed to unpack the model", exception)
                    _uiState.update { state -> state.copy(error = "Failed to unpack recognition model: ${exception.message}") }
                }
            )
        }
    }

    fun startMicRecognition() {
        if (!uiState.value.isModelReady || voskManager == null) {
            _uiState.update { it.copy(error = "Recognition service not ready.") }
            return
        }
        if (uiState.value.isRecording) return // Avoid re-starting

        Log.d("VoskRec", "Starting Mic Recognition (Max Duration: ${MAX_RECORDING_DURATION_MS}ms, Silence: ${SILENCE_DELAY_MS}ms)")
        resetTranscriptionState(clearUiToo = true)
        // Set initial recording state including timer display
        _uiState.update { it.copy(isRecording = true, remainingRecordingTime = MAX_RECORDING_DURATION_S) }
        voskManager?.startMicrophoneRecognition()

        // Start BOTH timers
        startSilenceTimer()
        startMaxDurationTimer() // This will now handle the countdown display
    }

    // Called manually by Stop button press
    fun stopRecognitionAndSend() {
        if (!uiState.value.isRecording) return

        Log.d("VoskRec", "Manual Stop Initiated")
        cancelAllTimers() // <-- Cancel both timers
        voskManager?.stopRecognition() // Stop Vosk listener

        // Process whatever is left in the buffer
        processCurrentBufferAndSend(isFinalSend = true) // Treat as final send

        // isRecording state is set after processing in processCurrentBufferAndSend if isFinalSend=true
    }

    // --- Timer Management ---

    private fun startSilenceTimer() {
        silenceDetectionJob?.cancel() // Cancel previous if any
        if (!uiState.value.isRecording) return // Safety check

        silenceDetectionJob = viewModelScope.launch {
            delay(SILENCE_DELAY_MS)
            Log.d("VoskSilence", "Silence detected.")
            if (uiState.value.isRecording) { // Check if still recording
                processCurrentBufferAndSend(isFinalSend = false) // Process buffer, but don't stop recording
            }
        }
        Log.v("VoskSilence", "Silence timer started/reset.")
    }

    private fun startMaxDurationTimer() {
        maxRecordingJob?.cancel()
        if (!uiState.value.isRecording) return // Safety check

        // Ensure timer starts at the correct value in UI state immediately
        _uiState.update { it.copy(remainingRecordingTime = MAX_RECORDING_DURATION_S) }

        maxRecordingJob = viewModelScope.launch {
            Log.v("VoskMaxTime", "Max duration timer started (Countdown).")
            for (remainingSeconds in MAX_RECORDING_DURATION_S downTo 1) {
                // Update UI state with remaining time
                // Check isActive in case the job was cancelled externally
                if (!isActive) {
                    Log.d("VoskMaxTime", "Countdown loop cancelled.")
                    // Reset timer display if cancelled prematurely
                    if (uiState.value.remainingRecordingTime != null) {
                        _uiState.update { it.copy(remainingRecordingTime = null) }
                    }
                    return@launch // Exit the coroutine
                }
                _uiState.update { it.copy(remainingRecordingTime = remainingSeconds) }
                Log.v("VoskMaxTime", "Time remaining: $remainingSeconds s")
                delay(1000L) // Wait 1 second
            }

            // --- Countdown Finished ---
            Log.d("VoskMaxTime", "Maximum recording duration (${MAX_RECORDING_DURATION_MS}ms) reached.")
            // Check if still recording *after* the loop finishes
            if (uiState.value.isRecording) {
                cancelAllTimers() // Stop silence timer too (already handled by maxRecordingJob cancel check)
                voskManager?.stopRecognition() // Stop Vosk
                // Process final buffer, this will also set remainingRecordingTime = null
                processCurrentBufferAndSend(isFinalSend = true)
            } else {
                // If manually stopped during the last second, ensure timer display is cleared
                if (uiState.value.remainingRecordingTime != null) {
                    _uiState.update { it.copy(remainingRecordingTime = null) }
                }
            }
        }
    }

    private fun cancelAllTimers() {
        silenceDetectionJob?.cancel()
        maxRecordingJob?.cancel() // This will trigger the isActive check in the loop
        silenceDetectionJob = null
        maxRecordingJob = null
        // Clear the timer display immediately on cancel
        if (uiState.value.remainingRecordingTime != null) {
            _uiState.update { it.copy(remainingRecordingTime = null) }
        }
        Log.v("VoskTimer", "All timers cancelled.")
    }

    // --- Processing Logic ---
    private fun processCurrentBufferAndSend(isFinalSend: Boolean) {
        // Prevent processing if not recording (unless final) or already loading
        if ((!uiState.value.isRecording && !isFinalSend) || uiState.value.isLoading) {
            Log.w("VoskProc", "Processing skipped. Recording: ${uiState.value.isRecording}, Loading: ${uiState.value.isLoading}, isFinalSend: $isFinalSend")
            if (isFinalSend && uiState.value.isRecording) {
                // Ensure state consistency if skipped during final send
                _uiState.update { it.copy(isRecording = false, partialText = "", remainingRecordingTime = null) } // Clear timer here too
            }
            return
        }

        // Combine the buffered results and the *last known* internal partial text
        val transcription = (recognitionResultsBuffer + currentPartialText).trim()
        Log.d("VoskProc", "Processing buffer: '$transcription'. isFinalSend: $isFinalSend")

        // Keep track of the specific partial text being processed now
        val processedPartial = currentPartialText

        // Clear internal buffers *before* async operations
        resetTranscriptionState(clearUiToo = false) // Clear internal only

        if (transcription.isNotEmpty()) {
            // 1. Create the final message object
            val userMessage = ChatMessage(transcription, true)

            // 2. Add the final message bubble to the list
            _uiState.value.messageList.add(0, userMessage)

            // 3. Check if the UI partial matches what we just processed, and clear it if so.
            _uiState.update { currentState ->
                if (currentState.partialText == processedPartial) {
                    currentState.copy(partialText = "")
                } else {
                    currentState // Keep the newer partial
                }
            }

            // 4. Send to backend
            sendMessageToBackend(transcription)

        } else {
            Log.w("VoskProc", "Processing buffer called, but result was empty.")
            // Even if empty, clear the UI partial if it matches what was (not) processed
            _uiState.update { currentState ->
                if (currentState.partialText == processedPartial) {
                    currentState.copy(partialText = "")
                } else {
                    currentState
                }
            }
        }

        // If this was the definitive end of recording
        if (isFinalSend) {
            // Set final state: not recording, no partial text, no remaining time
            _uiState.update { it.copy(isRecording = false, partialText = "", remainingRecordingTime = null) }
            Log.d("VoskProc", "Final Send processed, setting isRecording=false, timer display cleared.")
        }
        // Otherwise, if triggered by silence and still recording, restart the silence timer
        else if (uiState.value.isRecording) {
            startSilenceTimer()
        }
    }

    // Clears internal buffers and optionally the UI partial text display
    private fun resetTranscriptionState(clearUiToo: Boolean) {
        recognitionResultsBuffer = ""
        currentPartialText = ""
        if (clearUiToo) {
            // Only clear UI state's partial text when explicitly told
            _uiState.update { it.copy(partialText = "") }
        }
        Log.v("VoskState", "Transcription state reset. Clear UI: $clearUiToo")
    }


    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            try {
                val partial = JSONObject(hypothesis).optString("partial", "")
                if (partial != currentPartialText) {
                    currentPartialText = partial
                    _uiState.update { it.copy(partialText = partial) }
                    startSilenceTimer()
                    Log.v("VoskRec", "Partial: '$partial' - Silence Timer Reset")
                }
            } catch (e: Exception) { Log.e("VoskRec", "Partial parse error", e) }
        }
        override fun onResult(hypothesis: String) {
            try {
                val text = JSONObject(hypothesis).optString("text", "")
                if (text.isNotEmpty()) {
                    recognitionResultsBuffer += "$text "
                    currentPartialText = ""
                    startSilenceTimer()
                    Log.d("VoskRec", "Result segment: '$text' - Buffer: '$recognitionResultsBuffer' - Silence Timer Reset")
                }
            } catch (e: Exception) { Log.e("VoskRec", "Result parse error", e) }
        }
        override fun onFinalResult(hypothesis: String) { Log.d("VoskRec", "Final Result (usually empty): '$hypothesis'") }
        override fun onError(e: Exception) {
            Log.e("VoskRec", "Recognition Error", e)
            cancelAllTimers()
            resetTranscriptionState(clearUiToo = true)
            _uiState.update { it.copy(error = "Recognition error: ${e.message}", isRecording = false) }
        }
        override fun onTimeout() {
            Log.w("VoskRec", "Vosk Recognition Internal Timeout")
            cancelAllTimers()
            _uiState.update { it.copy(error = "Recognition stopped due to timeout") }
            processCurrentBufferAndSend(isFinalSend = true)
            if(uiState.value.isRecording) { _uiState.update { it.copy(isRecording = false) } }
        }
    }

    // Make sure AudioPlayer is released when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        Log.d("ChatDetailViewModel", "ViewModel cleared, shutting down Vosk, timers, and AudioPlayer.")
        cancelAllTimers()
        voskManager?.stopRecognition()
        AudioPlayer.release() // Release MediaPlayer resources
    }


    // --- Chat Data Methods ---
    private fun loadChatHistory() {
        Log.d("ChatDetailViewModel", "Loading history for chatId: $chatId")
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = chatRepository.getChatHistory(chatId)) {
                is Result.Success -> {
                    Log.d("ChatDetailViewModel", "History loaded successfully. ${result.data.history.size} messages.")
                    val messages = result.data.history.map { apiMsg ->
                        ChatMessage(
                            text = apiMsg.parts.firstOrNull()?.text ?: "",
                            isUser = apiMsg.role == "user"
                        )
                    }
                    _uiState.update {
                        it.copy(
                            chatTitle = result.data.title,
                            messageList = mutableStateListOf<ChatMessage>().apply { addAll(messages) },
                            isLoading = false, error = null
                        )
                    }
                }
                is Result.Error -> {
                    Log.e("ChatDetailViewModel", "Error loading history: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load history: ${result.message}") }
                }
            }
        }
    }

    // --- MODIFIED: sendMessageToBackend ---
    private fun sendMessageToBackend(prompt: String) {
        Log.d("ChatDetailViewModel", "Sending message to backend: '$prompt'")
        if (uiState.value.isLoading) {
            Log.w("ChatDetailViewModel", "Already processing a message, skipping new request for '$prompt'")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            // Use standard Result wrapper now
            when (val result = chatRepository.sendMessage(chatId, prompt)) {
                is Result.Success -> {
                    val responseData = result.data // This is SendMessageApiResponse
                    Log.d("ChatDetailViewModel", "Received response from backend. TTS Error: ${responseData.ttsError}")

                    // --- Play Audio if available ---
                    responseData.audioContent?.let { base64Audio ->
                        if (base64Audio.isNotEmpty()) {
                            Log.d("ChatDetailViewModel", "Attempting to play Base64 audio.")
                            AudioPlayer.playAudioFromBase64(getApplication(), base64Audio) {
                                Log.d("ChatDetailViewModel", "Audio playback finished.")
                            }
                        } else {
                            Log.w("ChatDetailViewModel", "Received empty audioContent string.")
                        }
                    } ?: run {
                        // Audio content was null
                        Log.w("ChatDetailViewModel", "No audio content received. TTS Error: ${responseData.ttsError}")
                        // Optionally show the TTS error to the user if it exists
                        if (responseData.ttsError != null) {
                            _uiState.update { it.copy(error = "TTS failed: ${responseData.ttsError}") }
                        }
                    }

                    // --- Add/Confirm Text Message ---
                    // The text (`responseData.message`) is always present in a successful response.
                    // Add it to the list if it's not already there from optimistic update.
                    val modelMessage = ChatMessage(responseData.message, false)
                    if (_uiState.value.messageList.none { it.text == responseData.message && !it.isUser }) {
                        Log.d("ChatDetailViewModel", "Adding model text message to UI.")
                        _uiState.value.messageList.add(0, modelMessage)
                    } else {
                        Log.d("ChatDetailViewModel", "Model text message already present optimistically.")
                    }

                    // Clear loading state AFTER processing response
                    _uiState.update { it.copy(isLoading = false) }
                    // Clear specific TTS error shown above if needed after a delay, or let next error override
                }
                is Result.Error -> {
                    // Handle API call error (network, server error 5xx, etc.)
                    Log.e("ChatDetailViewModel", "API Error sending message: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, error = "API Error: ${result.message}") }
                    // Consider reverting optimistic user message add here
                }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}


// --- UI State Data Class (Keep as is) ---
data class ChatDetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val chatTitle: String = "Chat",
    val messageList: MutableList<ChatMessage> = mutableStateListOf(),
    val isRecording: Boolean = false,
    val isModelReady: Boolean = false,
    val partialText: String = "",
    val remainingRecordingTime: Int? = null
)