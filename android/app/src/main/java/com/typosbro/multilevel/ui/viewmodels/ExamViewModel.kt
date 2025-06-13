// In: ui/viewmodels/ExamViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.CueCard
import com.typosbro.multilevel.data.remote.models.ExamStepRequest
import com.typosbro.multilevel.data.remote.models.ExamStepResponse
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import com.typosbro.multilevel.features.vosk.VoskRecognitionManager
import com.typosbro.multilevel.util.AudioPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model // Keep this import
import org.vosk.android.RecognitionListener
import org.vosk.android.StorageService
import java.util.*

// ExamPart and ExamUiState data classes remain the same...
enum class ExamPart { NOT_STARTED, PART_1, PART_2_PREP, PART_2_SPEAKING, PART_3, FINISHED, ANALYSIS_COMPLETE }

data class ExamUiState(
    val currentPart: ExamPart = ExamPart.NOT_STARTED,
    val examinerMessage: String? = null,
    val isExaminerSpeaking: Boolean = false,
    val isUserListening: Boolean = false,
    val partialTranscription: String = "",
    val part2CueCard: CueCard? = null,
    val timerValue: Int = 0,
    val isModelReady: Boolean = false,
    val error: String? = null,
    val finalResultId: String? = null
)


class ExamViewModel(
    application: Application,
    private val chatRepository: ChatRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState = _uiState.asStateFlow()

    // --- Core Exam State ---
    private val fullTranscript = mutableListOf<TranscriptEntry>()
    private var isFinalQuestion = false
    private var timerJob: Job? = null

    // --- VOSK & TTS State (voskModel is now declared) ---
    private var voskModel: Model? = null // <<< FIX IS HERE
    private var voskManager: VoskRecognitionManager? = null
    private val audioPlaybackChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var isCurrentlyPlayingAudio = false

    init {
        initVoskModel()
        initTtsAndAudioPlayer()
    }

    // --- Public Functions (Called from UI) ----
    fun startExam() {
        _uiState.update { it.copy(currentPart = ExamPart.PART_1) }
        fullTranscript.clear()
        isFinalQuestion = false

        viewModelScope.launch {
            when (val result = chatRepository.getInitialExamQuestion()) {
                is Result.Success -> handleBackendResponse(result.data)
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun startUserSpeechRecognition() {
        if (!uiState.value.isModelReady || voskManager == null || uiState.value.isUserListening) return
        _uiState.update { it.copy(isUserListening = true, partialTranscription = "") }
        voskManager?.startMicrophoneRecognition()
    }

    fun stopUserSpeechRecognition() {
        if (!uiState.value.isUserListening) return
        _uiState.update { it.copy(isUserListening = false) }
        voskManager?.stopRecognition() // Listener's onResult will handle the final text
    }

    // --- Private Exam Flow Logic ---
    private fun handleBackendResponse(response: ExamStepResponse) {
        fullTranscript.add(TranscriptEntry("Examiner", response.examinerLine))
        isFinalQuestion = response.isFinalQuestion
        _uiState.update {
            it.copy(
                examinerMessage = response.examinerLine,
                currentPart = ExamPart.entries[response.nextPart],
                part2CueCard = response.cueCard
            )
        }
        response.inputIds?.let { synthesizeAndPlay(it.map { id -> id.toLong() }) }
        when (_uiState.value.currentPart) {
            ExamPart.PART_2_PREP -> startPart2PrepTimer()
            else -> { /* Other parts wait for TTS to finish */ }
        }
    }

    private fun processUserTranscription(text: String) {
        if (text.isBlank()) {
            if (!isFinalQuestion) startUserSpeechRecognition()
            return
        }
        fullTranscript.add(TranscriptEntry("User", text))
        if (isFinalQuestion) {
            endExamAndAnalyze()
            return
        }
        viewModelScope.launch {
            val request = ExamStepRequest(
                part = uiState.value.currentPart.ordinal,
                userInput = text,
                transcriptContext = fullTranscript.joinToString("\n") { "${it.speaker}: ${it.text}" }
            )
            when (val result = chatRepository.getNextExamStep(request)) {
                is Result.Success -> handleBackendResponse(result.data)
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun startPart2PrepTimer() {
        countdownTimer(60) {
            _uiState.update { it.copy(currentPart = ExamPart.PART_2_SPEAKING) }
            // Here you should ideally fetch the "start speaking" line from your backend
            // For simplicity, we create a dummy response.
            handleBackendResponse(
                ExamStepResponse(
                    examinerLine = "Your preparation time is up. Please start speaking now.",
                    nextPart = ExamPart.PART_2_SPEAKING.ordinal,
                    cueCard = _uiState.value.part2CueCard, // Keep showing the card
                    isFinalQuestion = false,
                    inputIds = listOf() // Ideally get real input_ids for this line
                )
            )
            startPart2SpeakingTimer()
        }
    }

    private fun startPart2SpeakingTimer() {
        countdownTimer(120) {
            stopUserSpeechRecognition()
        }
    }

    private fun endExamAndAnalyze() {
        _uiState.update { it.copy(currentPart = ExamPart.FINISHED) }
        viewModelScope.launch {
            when (val result = chatRepository.analyzeFullExam(fullTranscript)) {
                is Result.Success -> {
                    _uiState.update { it.copy(finalResultId = result.data.resultId, currentPart = ExamPart.ANALYSIS_COMPLETE) }
                }
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun countdownTimer(durationSeconds: Int, onFinish: () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(timerValue = durationSeconds) }
            for (i in durationSeconds downTo 1) {
                if (!isActive) return@launch
                delay(1000)
                _uiState.update { it.copy(timerValue = it.timerValue - 1) }
            }
            onFinish()
        }
    }

    // --- VOSK & TTS/AUDIO PLAYER IMPLEMENTATION ---
    private fun initVoskModel() {
        StorageService.unpack(getApplication(), "model-en-us", "model",
            { model ->
                voskModel = model // This now correctly assigns to the class property
                voskModel?.let {
                    voskManager = VoskRecognitionManager(getApplication(), it, recognitionListener)
                    _uiState.update { state -> state.copy(isModelReady = true) }
                }
            },
            { e -> _uiState.update { it.copy(error = "Failed to init model: ${e.message}") } }
        )
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            val partialText = JSONObject(hypothesis).optString("partial", "")
            _uiState.update { it.copy(partialTranscription = partialText) }
        }

        override fun onResult(hypothesis: String) {
            val finalText = JSONObject(hypothesis).optString("text", "")
            _uiState.update { it.copy(isUserListening = false, partialTranscription = "") }
            processUserTranscription(finalText)
        }

        override fun onError(e: Exception) {
            _uiState.update { it.copy(isUserListening = false, error = "Vosk error: ${e.message}") }
        }
        override fun onFinalResult(hypothesis: String?) { /* Not used */ }
        override fun onTimeout() { stopUserSpeechRecognition() }
    }

    private fun initTtsAndAudioPlayer() {
        viewModelScope.launch { OnnxRuntimeManager.initialize(getApplication()) }
        observeAudioPlaybackRequests()
    }

    private fun synthesizeAndPlay(inputIds: List<Long>) {
        if (inputIds.isEmpty()) {
            if (_uiState.value.currentPart != ExamPart.PART_2_PREP) {
                startUserSpeechRecognition()
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExaminerSpeaking = true) }
            // This function is defined in AudioPlayer.kt (see next section)
            val audioBytes = AudioPlayer.createAudioAndConvertToWav(inputIds, getApplication())
            audioPlaybackChannel.send(audioBytes)
        }
    }

    private fun observeAudioPlaybackRequests() {
        viewModelScope.launch {
            audioPlaybackChannel.consumeAsFlow().collect { audioData ->
                isCurrentlyPlayingAudio = true
                AudioPlayer.playAudio(getApplication(), audioData) {
                    isCurrentlyPlayingAudio = false
                    _uiState.update { it.copy(isExaminerSpeaking = false) }
                    val currentPart = _uiState.value.currentPart
                    if (currentPart != ExamPart.PART_2_PREP && currentPart != ExamPart.FINISHED) {
                        startUserSpeechRecognition()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voskManager?.stopRecognition()
        timerJob?.cancel()
        AudioPlayer.release()
    }
}