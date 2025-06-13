package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.ChatStreamEvent
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.features.vosk.VoskRecognitionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.StorageService
import com.typosbro.multilevel.data.repositories.Result // For loadChatHistory
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import com.typosbro.multilevel.ui.component.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import com.typosbro.multilevel.util.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel // For queuing audio playback requests
import kotlinx.coroutines.flow.receiveAsFlow // To consume from the channel
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.LinkedList // For a simple queue
import java.util.Queue
import kotlinx.coroutines.channels.actor // Might not be needed with simpler channel approach
import kotlinx.coroutines.flow.consumeAsFlow

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
    private var recognitionResultsBuffer = ""
    private var currentPartialText = ""
    private var silenceDetectionJob: Job? = null
    private var maxRecordingJob: Job? = null
    private val SILENCE_DELAY_MS = 10000L
    private val MAX_RECORDING_DURATION_S = 30
    private val MAX_RECORDING_DURATION_MS = MAX_RECORDING_DURATION_S * 1000L

    // State for streaming bot response
    private var currentBotMessageId: String? = null
    private var accumulatedTextForStream: String = ""

    private val ttsSynthesisRequestChannel = Channel<List<Long>>(Channel.UNLIMITED)

    // Audio Playback Queueing
    private val audioPlaybackQueue: Queue<ByteArray> = LinkedList()
    private var isCurrentlyPlayingAudio = false
    private val audioPlaybackDataChannel = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        Log.d("ChatDetailViewModel", "Initializing for chatId: $chatId")
        loadChatHistory()
        initVoskModel()
        viewModelScope.launch {
            OnnxRuntimeManager.initialize(getApplication<Application>().applicationContext)
        }
        // Launch a coroutine to process the audio playback queue
        observeAudioPlaybackRequests() // Renamed for clarity
        observeTTSSynthesisRequests()
    }
    // --- Vosk Methods (Transcription Input) ---
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
                    _uiState.update { state -> state.copy(error = "Failed to unpack model: ${exception.message}") }
                }
            )
        }
    }

    fun startMicRecognition() {
        if (!uiState.value.isModelReady || voskManager == null) {
            _uiState.update { it.copy(error = "Recognition service not ready.") }
            return
        }
        if (uiState.value.isRecording || uiState.value.isStreamingMessage) { // Don't record if already recording or streaming
            Log.w("ChatDetailViewModel", "Start mic skipped. Recording: ${uiState.value.isRecording}, Streaming: ${uiState.value.isStreamingMessage}")
            return
        }

        Log.d("VoskRec", "Starting Mic Recognition (Max: ${MAX_RECORDING_DURATION_MS}ms, Silence: ${SILENCE_DELAY_MS}ms)")
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
        processVoskBufferAndSendMessage(isFinalSend = true)
    }

    private fun startSilenceTimer() { /* ... (same as before) ... */
        silenceDetectionJob?.cancel()
        if (!uiState.value.isRecording) return
        silenceDetectionJob = viewModelScope.launch {
            delay(SILENCE_DELAY_MS)
            Log.d("VoskSilence", "Silence detected.")
            if (uiState.value.isRecording) { processVoskBufferAndSendMessage(isFinalSend = false) }
        }
        Log.v("VoskSilence", "Silence timer started/reset.")
    }

    private fun startMaxDurationTimer() { /* ... (same as before) ... */
        maxRecordingJob?.cancel()
        if (!uiState.value.isRecording) return
        _uiState.update { it.copy(remainingRecordingTime = MAX_RECORDING_DURATION_S) }
        maxRecordingJob = viewModelScope.launch {
            Log.v("VoskMaxTime", "Max duration timer started.")
            for (remainingSeconds in MAX_RECORDING_DURATION_S downTo 1) {
                if (!isActive) { Log.d("VoskMaxTime", "Countdown cancelled."); return@launch }
                _uiState.update { it.copy(remainingRecordingTime = remainingSeconds) }
                delay(1000L)
            }
            Log.d("VoskMaxTime", "Max recording duration reached.")
            if (uiState.value.isRecording) {
                cancelAllTimers()
                voskManager?.stopRecognition()
                processVoskBufferAndSendMessage(isFinalSend = true)
            }
        }
    }

    private fun cancelAllTimers() { /* ... (same as before) ... */
        silenceDetectionJob?.cancel(); maxRecordingJob?.cancel()
        silenceDetectionJob = null; maxRecordingJob = null
        if (uiState.value.remainingRecordingTime != null) {
            _uiState.update { it.copy(remainingRecordingTime = null) }
        }
        Log.v("VoskTimer", "All timers cancelled.")
    }

    private fun processVoskBufferAndSendMessage(isFinalSend: Boolean) {
        if ((!uiState.value.isRecording && !isFinalSend) || uiState.value.isStreamingMessage) {
            Log.w("VoskProc", "Processing Vosk buffer skipped. Recording: ${uiState.value.isRecording}, Streaming: ${uiState.value.isStreamingMessage}, isFinalSend: $isFinalSend")
            if (isFinalSend && uiState.value.isRecording) {
                _uiState.update { it.copy(isRecording = false, partialText = "", remainingRecordingTime = null) }
            }
            return
        }

        val transcription = (recognitionResultsBuffer + currentPartialText).trim()
        Log.d("VoskProc", "Processing Vosk buffer: '$transcription'. isFinalSend: $isFinalSend")
        val processedPartial = currentPartialText
        resetTranscriptionState(clearUiToo = false)

        if (transcription.isNotEmpty()) {
            val userMessage = ChatMessage(text = transcription, isUser = true)
            _uiState.update {
                val newList = it.messageList.toMutableList(); newList.add(0, userMessage)
                it.copy(messageList = newList.toMutableList()) // Ensure new list instance for recomposition
            }
            _uiState.update { cs -> if (cs.partialText == processedPartial) cs.copy(partialText = "") else cs }

            // Call the streaming backend method
            sendStreamMessageToBackend(transcription)
        } else {
            Log.w("VoskProc", "Vosk buffer processed, but result was empty.")
            _uiState.update { cs -> if (cs.partialText == processedPartial) cs.copy(partialText = "") else cs }
        }

        if (isFinalSend) {
            _uiState.update { it.copy(isRecording = false, partialText = "", remainingRecordingTime = null) }
            Log.d("VoskProc", "Final Vosk Send processed, isRecording=false.")
        } else if (uiState.value.isRecording) {
            startSilenceTimer()
        }
    }

    private fun resetTranscriptionState(clearUiToo: Boolean) { /* ... (same as before) ... */
        recognitionResultsBuffer = ""; currentPartialText = ""
        if (clearUiToo) { _uiState.update { it.copy(partialText = "") } }
        Log.v("VoskState", "Transcription state reset. Clear UI: $clearUiToo")
    }

    private val recognitionListener = object : RecognitionListener { /* ... (same as before) ... */
        override fun onPartialResult(hypothesis: String) {
            try {
                val partial = JSONObject(hypothesis).optString("partial", "")
                if (partial != currentPartialText) { currentPartialText = partial; _uiState.update { it.copy(partialText = partial) }; startSilenceTimer() }
            } catch (e: Exception) { Log.e("VoskRec", "Partial parse error", e) }
        }
        override fun onResult(hypothesis: String) {
            try {
                val text = JSONObject(hypothesis).optString("text", "")
                if (text.isNotEmpty()) { recognitionResultsBuffer += "$text "; currentPartialText = ""; startSilenceTimer() }
            } catch (e: Exception) { Log.e("VoskRec", "Result parse error", e) }
        }
        override fun onFinalResult(hypothesis: String) { Log.d("VoskRec", "Final Result (Vosk): '$hypothesis'") }
        override fun onError(e: Exception) { Log.e("VoskRec", "Vosk Error", e); cancelAllTimers(); resetTranscriptionState(true); _uiState.update { it.copy(error = "Vosk error: ${e.message}", isRecording = false) } }
        override fun onTimeout() { Log.w("VoskRec", "Vosk Timeout"); cancelAllTimers(); processVoskBufferAndSendMessage(true); if(uiState.value.isRecording) { _uiState.update { it.copy(isRecording = false) } } }
    }


    // --- Chat Data & Streaming Message Handling ---
    private fun loadChatHistory() {
        Log.d("ChatDetailViewModel", "Loading history for chatId: $chatId")
        _uiState.update { it.copy(isLoading = true) } // isLoading for initial history
        viewModelScope.launch {
            when (val result = chatRepository.getChatHistory(chatId)) {
                is Result.Success -> {
                    val messages = result.data.history.map { apiMsg ->
                        ChatMessage(
                            text = apiMsg.parts.firstOrNull()?.text ?: "",
                            isUser = apiMsg.role == "user"
                        )
                    }
                    _uiState.update {
                        it.copy(
                            chatTitle = result.data.title,
                            messageList = mutableStateListOf<ChatMessage>().apply { addAll(messages.reversed()) },
                            isLoading = false, error = null
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = "History: ${result.message}") }
                }
            }
        }
    }

    private fun sendStreamMessageToBackend(prompt: String) {
        // Clear audio state for new message
        // No need to clear audioPlaybackQueue here if observeAudioPlaybackRequests handles old data correctly
        // when a new stream starts. But good to be sure.
        audioPlaybackQueue.clear()
        // audioPlaybackDataChannel might have pending items if a previous stream was aborted.
        // It's tricky to clear a channel directly without closing.
        // A simpler approach might be to associate data with a stream ID. For now, let's assume it's okay.
        isCurrentlyPlayingAudio = false
        AudioPlayer.stopPlayback() // Stop any ongoing playback from previous stream

        if (uiState.value.isStreamingMessage) {
            Log.w("ChatDetailViewModel", "Already streaming, skipping new request for '$prompt'")
            return
        }
        _uiState.update { it.copy(isStreamingMessage = true, error = null) }
        accumulatedTextForStream = ""
        currentBotMessageId = UUID.randomUUID().toString()
        audioPlaybackQueue.clear() // Clear queue for new message
        isCurrentlyPlayingAudio = false // Reset playback state

        val initialBotMessage = ChatMessage(id = currentBotMessageId!!, text = "...", isUser = false)
        _uiState.update {
            val newList = it.messageList.toMutableList(); newList.add(0, initialBotMessage)
            it.copy(messageList = newList.toMutableList())
        }

        val langCodeForTTS: String? = null
        val configKeyForTTS: String? = null

        chatRepository.sendMessageAndStream(chatId, prompt, langCodeForTTS, configKeyForTTS)
            .onEach { event ->
                viewModelScope.ensureActive()
                when (event) {
                    is ChatStreamEvent.TextChunk -> {
                        accumulatedTextForStream += event.text
                        updateBotMessageContent(currentBotMessageId, accumulatedTextForStream)
                    }
                    is ChatStreamEvent.InputIdsChunk -> {
                        Log.d("ChatStream", "Received input_ids for sentence: \"${event.sentence.take(30)}...\" Count: ${event.ids.size}")
                        if (event.ids.isNotEmpty()) {
                            // Synthesize and enqueue for playback
                            ttsSynthesisRequestChannel.send(event.ids.map { it.toLong() })
                        }
                    }
                    is ChatStreamEvent.PreprocessWarning -> {
                        Log.w("ChatStream", "Preprocess Warning: ${event.message} for sentence: \"${event.sentenceText?.take(30)}\"")
                        // Optionally, you could display a small, transient warning in the UI
                        // or append a note to the current bot message. For now, just logging.
                    }
                    is ChatStreamEvent.PreprocessError -> {
                        Log.e("ChatStream", "Preprocess Error: ${event.message} for sentence: \"${event.sentenceText?.take(30)}\"")
                        // Display a more noticeable error or append to the bot message
                        _uiState.update { it.copy(error = "TTS issue for a sentence: ${event.message.take(50)}") }
                        // You might also want to append something to the currentBotMessageId's text
                        // updateBotMessageContent(currentBotMessageId, accumulatedTextForStream + "\n[TTS Error for a part]", true)
                    }
                    is ChatStreamEvent.StreamEnd -> {
                        Log.d("ChatStream", "Stream ended: ${event.message}")
                        updateBotMessageContent(currentBotMessageId, accumulatedTextForStream)
                        _uiState.update { it.copy(isStreamingMessage = false) }
                        currentBotMessageId = null
                        // Signal end of synthesis requests if needed, or let existing ones complete
                    }
                    is ChatStreamEvent.StreamError -> { // ... (as before, clear queue potentially) ...
                        Log.e("ChatStream", "Stream error: ${event.message} ${event.details ?: ""}")
                        _uiState.update { it.copy(isStreamingMessage = false, error = "Stream Error: ${event.message}") }
                        updateBotMessageContent(currentBotMessageId, accumulatedTextForStream + "\n[Error in response stream]", true)
                        currentBotMessageId = null
                        // Clear TTS synthesis queue too if stream fails hard
                        // This requires clearing the ttsSynthesisRequestChannel, which is not trivial directly.
                        // One way is to send a special "cancel" message or close and reopen the channel.
                        // For now, let's assume synthesis for received items will proceed but playback queue is cleared.
                        audioPlaybackQueue.clear()
                        AudioPlayer.stopPlayback()
                    }
                }
            }
            .catch { e -> // ... (as before, clear queue potentially) ...
                Log.e("ChatDetailViewModel", "Exception collecting stream", e)
                _uiState.update { it.copy(isStreamingMessage = false, error = "Stream Collection Error: ${e.message}") }
                updateBotMessageContent(currentBotMessageId, accumulatedTextForStream + "\n[Error processing stream]", true)
                currentBotMessageId = null
                audioPlaybackQueue.clear()
                AudioPlayer.stopPlayback()
            }
            .launchIn(viewModelScope)
    }

    private fun updateBotMessageContent(messageId: String?, newText: String, isError: Boolean = false) {
        messageId ?: return
        _uiState.update { currentState ->
            val updatedList = currentState.messageList.map { chatMsg ->
                if (chatMsg.id == messageId) {
                    chatMsg.copy(text = newText.ifEmpty { if (isError) "[Error]" else "..." })
                } else {
                    chatMsg
                }
            }.toMutableList() // map returns List, convert back to MutableList if needed by UI state
            currentState.copy(messageList = updatedList)
        }
    }


    private fun observeTTSSynthesisRequests() {
        viewModelScope.launch(Dispatchers.IO) { // This actor runs on IO
            ttsSynthesisRequestChannel.consumeAsFlow().collect { sentenceInputIds ->
                // This collect block will process one list of IDs at a time, sequentially.
                if (sentenceInputIds.isEmpty()) return@collect

                Log.d("TTS_Synth_Queue", "Processing synthesis for: ${sentenceInputIds.size} ids.")
                try {
                    val session = OnnxRuntimeManager.getSession()
                    val appContext = getApplication<Application>().applicationContext
                    val voiceStyle = "bm_george"
                    val speed = 1.0f

                    val (audioFloatArray, sampleRate) = AudioPlayer.createAudio(
                        tokens = sentenceInputIds.toLongArray(),
                        voice = voiceStyle, speed = speed, session = session, context = appContext
                    )
                    val audioWavByteArray = AudioPlayer.convertFloatArrayToWavByteArray(audioFloatArray, sampleRate)

                    // Send synthesized data to the playback data channel
                    audioPlaybackDataChannel.send(audioWavByteArray)
                    Log.d("TTS_Synth_Queue", "Synthesized and sent audio data (${audioWavByteArray.size} bytes) to playback channel.")

                } catch (e: Exception) {
                    Log.e("TTS_Synth_Queue", "Error during TTS synthesis", e)
                    // Optionally send an error event to another channel to update UI
                    // or handle it here if it's just for logging.
                    // For now, we log and the sentence won't have audio.
                    // Consider sending a "playback_error_marker" to audioPlaybackDataChannel
                    // if you want the playback queue to know about a failed synthesis.
                }
            }
        }
    }

    // Renamed from synthesizeAndEnqueueAudio and no longer directly called by onEach
    // This function is now effectively replaced by the logic within observeTTSSynthesisRequests

    // Renamed from observeAudioRequests
    private fun observeAudioPlaybackRequests()  {
        viewModelScope.launch(Dispatchers.Main) { // Observer runs on Main for UI interaction with AudioPlayer
            audioPlaybackDataChannel.receiveAsFlow().collect { audioData ->
                audioPlaybackQueue.offer(audioData) // Add to the software queue
                playNextAudioInQueue() // Attempt to play if not already playing
            }
        }
    }

    private fun playNextAudioInQueue() {
        // Ensure this is called on the Main thread or where AudioPlayer can be safely interacted with
        if (isCurrentlyPlayingAudio || audioPlaybackQueue.isEmpty()) {
            if(isCurrentlyPlayingAudio) Log.d("TTS_Queue", "Already playing audio, new item will wait.")
            if(audioPlaybackQueue.isEmpty()) Log.d("TTS_Queue", "Audio queue is empty.")
            return
        }

        isCurrentlyPlayingAudio = true
        val audioDataToPlay = audioPlaybackQueue.poll() // Get and remove from queue

        if (audioDataToPlay != null) {
            Log.d("TTS_Queue", "Playing next audio from queue (${audioDataToPlay.size} bytes). Remaining: ${audioPlaybackQueue.size}")
            val appContext = getApplication<Application>().applicationContext
            AudioPlayer.playAudio(appContext, audioDataToPlay) {
                // This is the onCompletion callback from AudioPlayer
                Log.d("TTS_Queue", "Audio segment playback finished.")
                viewModelScope.launch(Dispatchers.Main) { // Ensure next play is also on Main
                    isCurrentlyPlayingAudio = false
                    playNextAudioInQueue() // Try to play the next item
                }
            }
        } else {
            isCurrentlyPlayingAudio = false // Should not happen if queue was not empty
            Log.w("TTS_Queue", "playNextAudioInQueue called but polled null from queue.")
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatDetailViewModel", "ViewModel cleared. Shutting down resources.")
        cancelAllTimers()
        voskManager?.stopRecognition()
        ttsSynthesisRequestChannel.close() // Close the synthesis request channel
        audioPlaybackDataChannel.close()   // Close the playback data channel
        audioPlaybackQueue.clear()
        AudioPlayer.release()
        isCurrentlyPlayingAudio = false
    }
}
// Ensure ChatDetailUiState has the isStreamingMessage flag
data class ChatDetailUiState(
    val isLoading: Boolean = false, // For initial history load
    val isStreamingMessage: Boolean = false, // True while bot response is streaming
    val error: String? = null,
    val chatTitle: String = "Chat",
    val messageList: MutableList<ChatMessage> = mutableStateListOf(),
    val isRecording: Boolean = false, // Vosk recording
    val isModelReady: Boolean = false, // Vosk model ready
    val partialText: String = "", // Vosk partial result
    val remainingRecordingTime: Int? = null // Vosk recording timer
)