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
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import com.typosbro.multilevel.ui.component.ChatMessage // Ensure this import points to your ChatMessage with UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.typosbro.multilevel.util.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// Removed java.util.UUID import as it's handled within ChatMessage.kt

class ChatDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository
) : AndroidViewModel(application) {
    // Removed: private val context = getApplication<Application>().applicationContext
    // Use getApplication<Application>().applicationContext directly where needed or pass 'application'

    val chatId: String = savedStateHandle["chatId"] ?: error("Chat ID not found in navigation args")

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState = _uiState.asStateFlow()

    // Vosk related state
    private var voskManager: VoskRecognitionManager? = null
    private var voskModel: Model? = null
    private var recognitionResultsBuffer = ""
    private var currentPartialText = ""

    // Timers
    private var silenceDetectionJob: Job? = null
    private var maxRecordingJob: Job? = null
    private val SILENCE_DELAY_MS = 10000L
    private val MAX_RECORDING_DURATION_S = 30
    private val MAX_RECORDING_DURATION_MS = MAX_RECORDING_DURATION_S * 1000L


    init {
        Log.d("ChatDetailViewModel", "Initializing for chatId: $chatId")
        loadChatHistory()
        initVoskModel()
        // Initialize ONNX Runtime. Consider if this is better done once application-wide
        // e.g., in MainViewModel or Application class, if not already.
        viewModelScope.launch {
            OnnxRuntimeManager.initialize(getApplication<Application>().applicationContext)
        }
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

    fun getSession() = OnnxRuntimeManager.getSession()

    fun startMicRecognition() {
        if (!uiState.value.isModelReady || voskManager == null) {
            _uiState.update { it.copy(error = "Recognition service not ready.") }
            return
        }
        if (uiState.value.isRecording) return

        Log.d("VoskRec", "Starting Mic Recognition (Max Duration: ${MAX_RECORDING_DURATION_MS}ms, Silence: ${SILENCE_DELAY_MS}ms)")
        resetTranscriptionState(clearUiToo = true)
        _uiState.update { it.copy(isRecording = true, remainingRecordingTime = MAX_RECORDING_DURATION_S) }
        voskManager?.startMicrophoneRecognition()
        startSilenceTimer()
        startMaxDurationTimer()
    }

    fun stopRecognitionAndSend() {
        if (!uiState.value.isRecording) return

        Log.d("VoskRec", "Manual Stop Initiated")
        cancelAllTimers()
        voskManager?.stopRecognition()
        processCurrentBufferAndSend(isFinalSend = true)
    }

    private fun startSilenceTimer() {
        silenceDetectionJob?.cancel()
        if (!uiState.value.isRecording) return

        silenceDetectionJob = viewModelScope.launch {
            delay(SILENCE_DELAY_MS)
            Log.d("VoskSilence", "Silence detected.")
            if (uiState.value.isRecording) {
                processCurrentBufferAndSend(isFinalSend = false)
            }
        }
        Log.v("VoskSilence", "Silence timer started/reset.")
    }

    private fun startMaxDurationTimer() {
        maxRecordingJob?.cancel()
        if (!uiState.value.isRecording) return

        _uiState.update { it.copy(remainingRecordingTime = MAX_RECORDING_DURATION_S) }

        maxRecordingJob = viewModelScope.launch {
            Log.v("VoskMaxTime", "Max duration timer started (Countdown).")
            for (remainingSeconds in MAX_RECORDING_DURATION_S downTo 1) {
                if (!isActive) {
                    Log.d("VoskMaxTime", "Countdown loop cancelled.")
                    if (uiState.value.remainingRecordingTime != null) {
                        _uiState.update { it.copy(remainingRecordingTime = null) }
                    }
                    return@launch
                }
                _uiState.update { it.copy(remainingRecordingTime = remainingSeconds) }
                Log.v("VoskMaxTime", "Time remaining: $remainingSeconds s")
                delay(1000L)
            }

            Log.d("VoskMaxTime", "Maximum recording duration (${MAX_RECORDING_DURATION_MS}ms) reached.")
            if (uiState.value.isRecording) {
                cancelAllTimers()
                voskManager?.stopRecognition()
                processCurrentBufferAndSend(isFinalSend = true)
            } else {
                if (uiState.value.remainingRecordingTime != null) {
                    _uiState.update { it.copy(remainingRecordingTime = null) }
                }
            }
        }
    }

    private fun cancelAllTimers() {
        silenceDetectionJob?.cancel()
        maxRecordingJob?.cancel()
        silenceDetectionJob = null
        maxRecordingJob = null
        if (uiState.value.remainingRecordingTime != null) {
            _uiState.update { it.copy(remainingRecordingTime = null) }
        }
        Log.v("VoskTimer", "All timers cancelled.")
    }

    private fun processCurrentBufferAndSend(isFinalSend: Boolean) {
        if ((!uiState.value.isRecording && !isFinalSend) || uiState.value.isLoading) {
            Log.w("VoskProc", "Processing skipped. Recording: ${uiState.value.isRecording}, Loading: ${uiState.value.isLoading}, isFinalSend: $isFinalSend")
            if (isFinalSend && uiState.value.isRecording) {
                _uiState.update { it.copy(isRecording = false, partialText = "", remainingRecordingTime = null) }
            }
            return
        }

        val transcription = (recognitionResultsBuffer + currentPartialText).trim()
        Log.d("VoskProc", "Processing buffer: '$transcription'. isFinalSend: $isFinalSend")

        val processedPartial = currentPartialText
        resetTranscriptionState(clearUiToo = false)

        if (transcription.isNotEmpty()) {
            // ChatMessage constructor will auto-generate a unique ID and current timestamp
            val userMessage = ChatMessage(text = transcription, isUser = true)
            _uiState.value.messageList.add(0, userMessage)

            _uiState.update { currentState ->
                if (currentState.partialText == processedPartial) {
                    currentState.copy(partialText = "")
                } else {
                    currentState
                }
            }
            sendMessageToBackend(transcription)
        } else {
            Log.w("VoskProc", "Processing buffer called, but result was empty.")
            _uiState.update { currentState ->
                if (currentState.partialText == processedPartial) {
                    currentState.copy(partialText = "")
                } else {
                    currentState
                }
            }
        }

        if (isFinalSend) {
            _uiState.update { it.copy(isRecording = false, partialText = "", remainingRecordingTime = null) }
            Log.d("VoskProc", "Final Send processed, setting isRecording=false, timer display cleared.")
        } else if (uiState.value.isRecording) {
            startSilenceTimer()
        }
    }

    private fun resetTranscriptionState(clearUiToo: Boolean) {
        recognitionResultsBuffer = ""
        currentPartialText = ""
        if (clearUiToo) {
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

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatDetailViewModel", "ViewModel cleared, shutting down Vosk, timers, and AudioPlayer.")
        cancelAllTimers()
        voskManager?.stopRecognition()
        AudioPlayer.release()
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
                        // ChatMessage constructor will auto-generate a unique ID.
                        // Timestamp will also be auto-generated to System.currentTimeMillis().
                        // If your API provided a timestamp for historical messages, you'd set it here.
                        // Since ApiMessage doesn't have a per-message timestamp, this is fine.
                        ChatMessage(
                            text = apiMsg.parts.firstOrNull()?.text ?: "",
                            isUser = apiMsg.role == "user"
                            // Example if apiMsg had a timestamp:
                            // timestamp = parseApiTimestamp(apiMsg.createdAt)
                        )
                    }
                    _uiState.update {
                        it.copy(
                            chatTitle = result.data.title,
                            // Add messages to the mutableStateListOf.
                            // Ensure they are added in the order expected by your UI (reverseLayout in LazyColumn means newest is at index 0).
                            // API typically returns oldest first, so if you addAll, then newest is at the end.
                            // If you want newest message at index 0 of messageList:
                            messageList = mutableStateListOf<ChatMessage>().apply { addAll(messages.reversed()) },
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

    private fun sendMessageToBackend(prompt: String) {
        Log.d("ChatDetailViewModel", "Sending message to backend: '$prompt'")
        if (uiState.value.isLoading) {
            Log.w("ChatDetailViewModel", "Already processing a message, skipping new request for '$prompt'")
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(chatId, prompt)) {
                is Result.Success -> {
                    val responseData = result.data
                    Log.d("ChatDetailViewModel", "Received response from backend. Message: '${responseData.message}', Preprocessed available: ${responseData.preprocessed != null}")

                    // ChatMessage constructor will auto-generate a unique ID and current timestamp
                    val modelMessage = ChatMessage(text = responseData.message, isUser = false)
                    if (_uiState.value.messageList.none { it.text == responseData.message && !it.isUser }) {
                        _uiState.value.messageList.add(0, modelMessage)
                        Log.d("ChatDetailViewModel", "Added model text message to UI.")
                    } else {
                        Log.d("ChatDetailViewModel", "Model text message already present optimistically or from previous attempt.")
                    }

                    val inputIdsList = responseData.preprocessed?.input_ids?.firstOrNull()
                    if (inputIdsList != null && inputIdsList.isNotEmpty()) {
                        Log.d("ChatDetailViewModel", "Attempting TTS with ${inputIdsList.size} input_ids.")
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val inputIdsLongArray = inputIdsList.map { it.toLong() }.toLongArray()
                                val session = getSession()
                                val currentContext = getApplication<Application>().applicationContext // Use current context

                                val voiceStyle = "bf_isabella"
                                val speed = 1.0f

                                Log.d("ChatDetailViewModel", "Calling AudioPlayer.createAudio with ${inputIdsLongArray.size} tokens, voice: $voiceStyle, speed: $speed")
                                val (audioFloatArray, sampleRate) = AudioPlayer.createAudio(
                                    tokens = inputIdsLongArray,
                                    voice = voiceStyle,
                                    speed = speed,
                                    session = session,
                                    context = currentContext
                                )
                                Log.d("ChatDetailViewModel", "TTS inference complete. Audio float array size: ${audioFloatArray.size}, Sample rate: $sampleRate")

                                val audioWavByteArray = AudioPlayer.convertFloatArrayToWavByteArray(audioFloatArray, sampleRate)
                                Log.d("ChatDetailViewModel", "WAV byte array created, size: ${audioWavByteArray.size}")

                                withContext(Dispatchers.Main) {
                                    AudioPlayer.playAudio(currentContext, audioWavByteArray) {
                                        Log.d("ChatDetailViewModel", "TTS Audio playback finished.")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ChatDetailViewModel", "Error during TTS generation or playback", e)
                                withContext(Dispatchers.Main) {
                                    _uiState.update { it.copy(error = "TTS Error: ${e.message}") }
                                }
                            }
                        }
                    } else {
                        Log.w("ChatDetailViewModel", "No input_ids found or list is empty. Skipping client-side TTS.")
                        if (responseData.ttsError != null && responseData.ttsError.isNotEmpty()) {
                            _uiState.update { it.copy(error = "TTS unavailable: ${responseData.ttsError}") }
                        } else if (responseData.preprocessed?.input_ids == null) {
                            _uiState.update { it.copy(error = "TTS data not available for this message.") }
                        }
                    }
                    _uiState.update { it.copy(isLoading = false) }

                }
                is Result.Error -> {
                    Log.e("ChatDetailViewModel", "API Error sending message: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, error = "API Error: ${result.message}") }
                }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}


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