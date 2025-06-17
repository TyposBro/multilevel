// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ChatDetailViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.ChatStreamEvent
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result
import com.typosbro.multilevel.features.whisper.Recorder
import com.typosbro.multilevel.features.whisper.engine.WhisperEngineNative
import com.typosbro.multilevel.navigation.AppDestinations
import com.typosbro.multilevel.ui.component.ChatMessage
import com.typosbro.multilevel.utils.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ChatDetailUiState(
    val isLoading: Boolean = true,
    val isRecording: Boolean = false,
    val isModelReady: Boolean = false, // For STT
    val isBotTyping: Boolean = false,
    val chatTitle: String = "Chat",
    val partialText: String = "",
    val messageList: List<ChatMessage> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context
) : ViewModel(), Recorder.RecorderListener {

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val chatId: String = savedStateHandle.get<String>(AppDestinations.CHAT_ID_ARG)!!

    private var whisperEngine: WhisperEngineNative? = null
    private var recorder: Recorder? = null

    private var isPlayingAudio = false
    private val audioQueue = mutableListOf<List<Int>>()

    init {
        loadChatHistory()
        initializeStt()
    }

    private fun initializeStt() {
        viewModelScope.launch(Dispatchers.IO) {
            whisperEngine = WhisperEngineNative(context).also {
                val modelFile = getAssetFile("whisper-tiny.en.tflite")
                it.initialize(modelFile.absolutePath, "", false)
            }
            recorder = Recorder(context, this@ChatDetailViewModel)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isModelReady = true) }
            }
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = chatRepository.getChatHistory(chatId)) {
                is Result.Success -> {
                    val history = result.data.history.map {
                        ChatMessage(
                            text = it.parts.firstOrNull()?.text ?: "",
                            isUser = it.role == "user"
                        )
                    }.reversed()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            chatTitle = result.data.title,
                            messageList = history
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun startMicRecognition() {
        if (recorder?.isRecording == true || !_uiState.value.isModelReady) return
        _uiState.update { it.copy(isRecording = true, partialText = "") }
        recorder?.start()
    }

    fun stopRecognitionAndSend() {
        if (recorder?.isRecording != true) return
        recorder?.stop() // This will trigger onRecordingStopped
    }

    // --- Recorder.RecorderListener Implementation ---

    override fun onDataReceived(samples: FloatArray) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = whisperEngine?.transcribeBuffer(samples)
            if (!result.isNullOrBlank()) {
                val currentText = _uiState.value.partialText
                _uiState.update { it.copy(partialText = currentText + result) }
            }
        }
    }

    override fun onRecordingStopped() {
        viewModelScope.launch {
            val userPrompt = _uiState.value.partialText.trim()
            _uiState.update { it.copy(isRecording = false, partialText = "") }

            if (userPrompt.isNotBlank()) {
                addMessage(ChatMessage(text = userPrompt, isUser = true))
                sendMessageToServer(userPrompt)
            }
        }
    }

    private fun sendMessageToServer(prompt: String) {
        _uiState.update { it.copy(isBotTyping = true) }
        addMessage(ChatMessage(text = "", isUser = false)) // Add empty bot message

        chatRepository.sendMessageAndStream(chatId, prompt, "en", null)
            .onEach { event ->
                when (event) {
                    is ChatStreamEvent.TextChunk -> {
                        _uiState.update { state ->
                            val updatedMessages = state.messageList.toMutableList()
                            val lastMessage = updatedMessages.first()
                            updatedMessages[0] = lastMessage.copy(text = lastMessage.text + event.text)
                            state.copy(messageList = updatedMessages)
                        }
                    }
                    is ChatStreamEvent.InputIdsChunk -> audioQueue.add(event.ids)
                    is ChatStreamEvent.StreamEnd -> {
                        _uiState.update { it.copy(isBotTyping = false) }
                        playAudioQueue()
                    }
                    is ChatStreamEvent.StreamError -> _uiState.update { it.copy(isBotTyping = false, error = event.message) }
                    else -> {} // Ignore other events for now
                }
            }
            .launchIn(viewModelScope)
    }

    private fun playAudioQueue() {
        if (audioQueue.isEmpty()) return
        isPlayingAudio = true
        playNextAudioInQueue()
    }

    private fun playNextAudioInQueue() {
        if (audioQueue.isEmpty()) {
            isPlayingAudio = false
            return
        }
        viewModelScope.launch {
            val audioBytes = AudioPlayer.createAudioAndConvertToWav(audioQueue.removeAt(0).map { it.toLong() }, context)
            AudioPlayer.playAudio(context, audioBytes) { playNextAudioInQueue() }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update {
            it.copy(messageList = listOf(message) + it.messageList)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun getAssetFile(assetName: String): File {
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { it.copyTo(file.outputStream()) }
        }
        return file
    }

    override fun onCleared() {
        super.onCleared()
        recorder?.stop()
        whisperEngine?.deinitialize()
        AudioPlayer.release()
    }
}