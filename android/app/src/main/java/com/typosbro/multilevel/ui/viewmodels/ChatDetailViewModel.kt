// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ChatDetailViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.R // Make sure this points to your project's R file
import com.typosbro.multilevel.data.remote.models.ChatStreamEvent
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result // For loadChatHistory
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import com.typosbro.multilevel.features.whisper.Recorder
import com.typosbro.multilevel.features.whisper.Whisper
import com.typosbro.multilevel.ui.component.ChatMessage
import com.typosbro.multilevel.utils.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class ChatDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository
) : AndroidViewModel(application) {

    val chatId: String = savedStateHandle["chatId"] ?: error("Chat ID not found in navigation args")

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState = _uiState.asStateFlow()

    // --- Whisper STT State ---
    private var whisper: Whisper? = null
    private var recorder: Recorder? = null
    private var accumulatedTranscription = ""

    // --- TTS & Audio Player State ---
    private var currentBotMessageId: String? = null
    private var accumulatedTextForStream: String = ""
    private val ttsSynthesisRequestChannel = Channel<List<Long>>(Channel.UNLIMITED)
    private val audioPlaybackDataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val audioPlaybackQueue: Queue<ByteArray> = LinkedList()
    private var isCurrentlyPlayingAudio = false

    init {
        Log.d("ChatDetailViewModel", "Initializing for chatId: $chatId")
        loadChatHistory()
        initWhisper() // New initialization method
        viewModelScope.launch {
            OnnxRuntimeManager.initialize(getApplication<Application>().applicationContext)
        }
        observeAudioPlaybackRequests()
        observeTTSSynthesisRequests()
    }

    // --- Whisper STT Methods (New Implementation) ---

    private fun initWhisper() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Define model and vocab files to be copied from res/raw
                val modelName = "whisper_tiny_en.tflite" // English-only model
                val vocabName = "filters_vocab_en.bin"
                val multilingual = false

                // Copy files to a cache directory where the native code can access them
                val modelFile = copyRawResourceToFile(modelName, R.raw.whisper_tiny_en)
                val vocabFile = copyRawResourceToFile(vocabName, R.raw.filters_vocab_en)

                if (modelFile == null || vocabFile == null) {
                    _uiState.update { it.copy(error = "Failed to prepare STT model files.") }
                    return@launch
                }

                // Initialize Whisper and Recorder
                whisper = Whisper(getApplication()).also {
                    it.setListener(whisperListener)
                    it.loadModel(modelFile, vocabFile, multilingual)
                }

                recorder = Recorder(getApplication()).also {
                    it.setListener(recorderListener)
                }

                _uiState.update { it.copy(isModelReady = true) }
                Log.d("WhisperInit", "Whisper Model and Recorder initialized successfully.")

            } catch (e: Exception) {
                Log.e("WhisperInit", "Failed to initialize Whisper", e)
                _uiState.update { it.copy(error = "Failed to initialize STT model: ${e.message}") }
            }
        }
    }

    private fun copyRawResourceToFile(fileName: String, resourceId: Int): File? {
        val context = getApplication<Application>()
        val file = File(context.cacheDir, fileName)
        if (file.exists()) {
            Log.d("WhisperInit", "File already exists in cache: ${file.path}")
            return file
        }

        try {
            val inputStream: InputStream = context.resources.openRawResource(resourceId)
            val outputStream: OutputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("WhisperInit", "Successfully copied $fileName to cache.")
            return file
        } catch (e: Exception) {
            Log.e("WhisperInit", "Error copying raw resource to file", e)
            return null
        }
    }

    private val whisperListener = object : Whisper.WhisperListener {
        override fun onUpdateReceived(message: String?) {
            Log.d("WhisperListener", "Update: $message")
            // Can be used to show "Processing..." state
        }

        override fun onResultReceived(result: String?) {
            if (result.isNullOrBlank()) return

            Log.d("WhisperListener", "Result: $result")
            // The Whisper engine seems to send results for chunks.
            // We accumulate them and show them as "partial" text.
            accumulatedTranscription += result
            _uiState.update { it.copy(partialText = accumulatedTranscription) }
        }
    }

    private val recorderListener = object : Recorder.RecorderListener {
        override fun onUpdateReceived(message: String?) {
            Log.d("RecorderListener", "Update: $message")
            if (message == Recorder.MSG_RECORDING_DONE) {
                // This is a good place to process the final accumulated transcription
                processFinalTranscription()
            }
        }

        override fun onDataReceived(samples: FloatArray?) {
            // This is the live audio data chunk. Send it to Whisper for processing.
            samples?.let {
                whisper?.writeBuffer(it)
            }
        }
    }

    fun startMicRecognition() {
        if (!uiState.value.isModelReady || recorder == null) {
            _uiState.update { it.copy(error = "Recognition service not ready.") }
            return
        }
        if (uiState.value.isRecording || uiState.value.isStreamingMessage) {
            return
        }

        Log.d("WhisperRec", "Starting Mic Recognition")
        accumulatedTranscription = "" // Reset for new recording
        _uiState.update { it.copy(isRecording = true, partialText = "") }
        recorder?.start()
    }

    fun stopRecognitionAndSend() {
        if (!uiState.value.isRecording) return
        Log.d("WhisperRec", "Manual Stop Initiated")
        _uiState.update { it.copy(isRecording = false) }
        recorder?.stop() // This will trigger MSG_RECORDING_DONE in the listener
    }

    private fun processFinalTranscription() {
        val finalTranscription = accumulatedTranscription.trim()
        Log.d("WhisperProc", "Processing final transcription: '$finalTranscription'")

        if (finalTranscription.isNotEmpty()) {
            val userMessage = ChatMessage(text = finalTranscription, isUser = true)
            _uiState.update {
                val newList = it.messageList.toMutableList()
                newList.add(0, userMessage)
                it.copy(messageList = newList.toMutableList(), partialText = "")
            }
            sendStreamMessageToBackend(finalTranscription)
        } else {
            _uiState.update { it.copy(partialText = "") }
        }
        accumulatedTranscription = "" // Clear buffer for next use
    }


    // --- Chat Data & Streaming Message Handling (Largely Unchanged) ---
    private fun loadChatHistory() {
        Log.d("ChatDetailViewModel", "Loading history for chatId: $chatId")
        _uiState.update { it.copy(isLoading = true) }
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
        audioPlaybackQueue.clear()
        isCurrentlyPlayingAudio = false
        AudioPlayer.stopPlayback()

        if (uiState.value.isStreamingMessage) {
            Log.w("ChatDetailViewModel", "Already streaming, skipping new request for '$prompt'")
            return
        }
        _uiState.update { it.copy(isStreamingMessage = true, error = null) }
        accumulatedTextForStream = ""
        currentBotMessageId = UUID.randomUUID().toString()

        val initialBotMessage = ChatMessage(id = currentBotMessageId!!, text = "...", isUser = false)
        _uiState.update {
            val newList = it.messageList.toMutableList(); newList.add(0, initialBotMessage)
            it.copy(messageList = newList.toMutableList())
        }

        chatRepository.sendMessageAndStream(chatId, prompt, null, null)
            .onEach { event ->
                when (event) {
                    is ChatStreamEvent.TextChunk -> {
                        accumulatedTextForStream += event.text
                        updateBotMessageContent(currentBotMessageId, accumulatedTextForStream)
                    }
                    is ChatStreamEvent.InputIdsChunk -> {
                        if (event.ids.isNotEmpty()) {
                            ttsSynthesisRequestChannel.send(event.ids.map { it.toLong() })
                        }
                    }
                    is ChatStreamEvent.StreamEnd -> {
                        Log.d("ChatStream", "Stream ended: ${event.message}")
                        updateBotMessageContent(currentBotMessageId, accumulatedTextForStream)
                        _uiState.update { it.copy(isStreamingMessage = false) }
                        currentBotMessageId = null
                    }
                    is ChatStreamEvent.StreamError -> {
                        Log.e("ChatStream", "Stream error: ${event.message}")
                        _uiState.update { it.copy(isStreamingMessage = false, error = "Stream Error: ${event.message}") }
                        updateBotMessageContent(currentBotMessageId, accumulatedTextForStream + "\n[Error in response stream]", true)
                        currentBotMessageId = null
                        audioPlaybackQueue.clear()
                        AudioPlayer.stopPlayback()
                    }
                    is ChatStreamEvent.PreprocessWarning -> { /* Logging is sufficient */ }
                    is ChatStreamEvent.PreprocessError -> { /* Logging is sufficient */ }
                }
            }
            .catch { e ->
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
            }.toMutableList()
            currentState.copy(messageList = updatedList)
        }
    }


    // --- TTS & Audio Playback Methods (Unchanged) ---
    private fun observeTTSSynthesisRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            ttsSynthesisRequestChannel.consumeAsFlow().collect { sentenceInputIds ->
                if (sentenceInputIds.isEmpty()) return@collect
                try {
                    val audioWavByteArray = AudioPlayer.createAudioAndConvertToWav(sentenceInputIds, getApplication())
                    audioPlaybackDataChannel.send(audioWavByteArray)
                } catch (e: Exception) {
                    Log.e("TTS_Synth_Queue", "Error during TTS synthesis", e)
                }
            }
        }
    }

    private fun observeAudioPlaybackRequests()  {
        viewModelScope.launch(Dispatchers.Main) {
            audioPlaybackDataChannel.receiveAsFlow().collect { audioData ->
                audioPlaybackQueue.offer(audioData)
                playNextAudioInQueue()
            }
        }
    }

    private fun playNextAudioInQueue() {
        if (isCurrentlyPlayingAudio || audioPlaybackQueue.isEmpty()) {
            return
        }
        isCurrentlyPlayingAudio = true
        val audioDataToPlay = audioPlaybackQueue.poll()
        if (audioDataToPlay != null) {
            AudioPlayer.playAudio(getApplication(), audioDataToPlay) {
                viewModelScope.launch(Dispatchers.Main) {
                    isCurrentlyPlayingAudio = false
                    playNextAudioInQueue()
                }
            }
        } else {
            isCurrentlyPlayingAudio = false
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatDetailViewModel", "ViewModel cleared. Shutting down resources.")
        recorder?.stop()
        whisper?.unloadModel()
        ttsSynthesisRequestChannel.close()
        audioPlaybackDataChannel.close()
        audioPlaybackQueue.clear()
        AudioPlayer.release()
    }
}

// Ensure ChatDetailUiState is updated (remove Vosk-specific fields)
data class ChatDetailUiState(
    val isLoading: Boolean = false,
    val isStreamingMessage: Boolean = false,
    val error: String? = null,
    val chatTitle: String = "Chat",
    val messageList: MutableList<ChatMessage> = mutableStateListOf(),
    // New/repurposed state for Whisper
    val isRecording: Boolean = false,
    val isModelReady: Boolean = false,
    val partialText: String = "", // Used for accumulating chunk results from Whisper
)