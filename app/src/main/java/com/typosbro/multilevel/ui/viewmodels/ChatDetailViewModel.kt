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
import kotlinx.coroutines.isActive // Import isActive

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

    // MODIFIED: startMaxDurationTimer - Now includes countdown logic
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

    // MODIFIED: cancelAllTimers - Explicitly clear remaining time display
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
    // MODIFIED: processCurrentBufferAndSend - Clear timer display on final send
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


    override fun onCleared() {
        super.onCleared()
        Log.d("VoskRec", "ViewModel cleared, shutting down Vosk and cancelling timers.")
        cancelAllTimers() // Ensures timer display is cleared
        voskManager?.stopRecognition() // Ensure Vosk is stopped
    }


    // --- Recognition Listener ---
    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            try {
                val partial = JSONObject(hypothesis).optString("partial", "")
                // Update internal and UI state only if the partial text changes
                if (partial != currentPartialText) {
                    currentPartialText = partial // Update internal tracking
                    _uiState.update { it.copy(partialText = partial) } // Update UI display
                    // Reset silence timer whenever there's new partial activity (blank or not)
                    startSilenceTimer()
                    Log.v("VoskRec", "Partial: '$partial' - Silence Timer Reset")
                }
            } catch (e: Exception) { Log.e("VoskRec", "Partial parse error", e) }
        }

        override fun onResult(hypothesis: String) {
            try {
                val text = JSONObject(hypothesis).optString("text", "")
                if (text.isNotEmpty()) {
                    // Append the confirmed text to the buffer
                    recognitionResultsBuffer += "$text "
                    // Clear the internal partial text tracker since we got a confirmed result
                    currentPartialText = ""
                    // Don't clear the UI partial immediately

                    startSilenceTimer() // Reset SILENCE timer on confirmed speech segment
                    Log.d("VoskRec", "Result segment: '$text' - Buffer: '$recognitionResultsBuffer' - Silence Timer Reset")
                }
            } catch (e: Exception) { Log.e("VoskRec", "Result parse error", e) }
        }

        override fun onFinalResult(hypothesis: String) {
            Log.d("VoskRec", "Final Result (usually empty): '$hypothesis'")
        }

        // MODIFIED: onError, onTimeout - Ensure timer display cleared
        override fun onError(e: Exception) {
            Log.e("VoskRec", "Recognition Error", e)
            cancelAllTimers() // This now clears the timer display
            resetTranscriptionState(clearUiToo = true) // Also clear internal buffers and UI partial
            _uiState.update { it.copy(error = "Recognition error: ${e.message}", isRecording = false) } // isRecording=false, remainingTime=null set by cancelAllTimers
        }

        override fun onTimeout() { // Vosk internal timeout
            Log.w("VoskRec", "Vosk Recognition Internal Timeout")
            cancelAllTimers() // This now clears the timer display
            _uiState.update { it.copy(error = "Recognition stopped due to timeout") }
            // Treat timeout as a final send event
            processCurrentBufferAndSend(isFinalSend = true) // This clears remaining time state too
            // Ensure isRecording is set to false after processing, although processCurrentBuffer handles it
            if(uiState.value.isRecording) {
                _uiState.update { it.copy(isRecording = false) }
            }
        }
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
                    }//.reversed() // Assuming LazyColumn reverseLayout = true

                    _uiState.update {
                        it.copy(
                            chatTitle = result.data.title,
                            messageList = mutableStateListOf<ChatMessage>().apply { addAll(messages) },
                            isLoading = false,
                            error = null
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

    private fun sendMessageToBackend(prompt: String) {
        Log.d("ChatDetailViewModel", "Sending message to backend: '$prompt'")
        if (uiState.value.isLoading) {
            Log.w("ChatDetailViewModel", "Already sending a message, skipping new request for '$prompt'")
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(chatId, prompt)) {
                is Result.Success -> {
                    Log.d("ChatDetailViewModel", "Received response from backend: '${result.data.message}'")
                    val modelMessage = ChatMessage(result.data.message, false)
                    _uiState.value.messageList.add(0, modelMessage) // Add model response
                    // Set loading false AFTER adding the message
                    _uiState.update { it.copy(isLoading = false, error = null) }
                }
                is Result.Error -> {
                    Log.e("ChatDetailViewModel", "Error sending message: ${result.message}")
                    // Set loading false even on error
                    _uiState.update { it.copy(isLoading = false, error = "Failed to get response: ${result.message}") }
                    // Optional: Revert optimistic user message add here if desired
                }
            }
        }
    }


    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

// --- UI State Data Class ---
data class ChatDetailUiState(
    val isLoading: Boolean = false, // Represents network loading state primarily
    val error: String? = null,
    val chatTitle: String = "Chat",
    val messageList: MutableList<ChatMessage> = mutableStateListOf(),
    val isRecording: Boolean = false, // Represents Vosk listener state
    val isModelReady: Boolean = false,
    val partialText: String = "", // Represents the text to show in the temporary partial bubble
    val remainingRecordingTime: Int? = null // Holds remaining time in seconds
)