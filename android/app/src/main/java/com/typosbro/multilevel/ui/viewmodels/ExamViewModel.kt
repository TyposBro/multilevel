// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ExamViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.*
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import com.typosbro.multilevel.features.vosk.VoskRecognitionManager
import com.typosbro.multilevel.utils.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.StorageService
import java.util.*

// --- Data classes remain the same ---
enum class ExamPart { NOT_STARTED, PART_1, PART_2_PREP, PART_2_SPEAKING, PART_3, FINISHED, ANALYSIS_COMPLETE }

data class ExamUiState(
    val currentPart: ExamPart = ExamPart.NOT_STARTED,
    val examinerMessage: String? = null,
    val isExaminerSpeaking: Boolean = false, // True while text is streaming OR audio is playing
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
    private var textStreamHasEnded = false // Flag to coordinate with audio queue

    // --- VOSK State ---
    private var voskModel: Model? = null
    private var voskManager: VoskRecognitionManager? = null

    // --- TTS & Audio Queuing System ---
    private var accumulatedTextForStream: String = ""
    private val ttsSynthesisRequestChannel = Channel<List<Long>>(Channel.UNLIMITED)
    private val audioPlaybackDataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val audioPlaybackQueue: Queue<ByteArray> = LinkedList()
    private var isCurrentlyPlayingAudio = false

    init {
        initVoskModel()
        initTtsAndAudioPlayer()
    }

    // --- Public Functions (Called from UI) ----
    fun startExam() {
        _uiState.update { it.copy(currentPart = ExamPart.PART_1, error = null) }
        fullTranscript.clear()
        isFinalQuestion = false

        viewModelScope.launch {
            // The initial question is simple, non-streaming is fine here.
            when (val result = chatRepository.getInitialExamQuestion()) {
                is Result.Success -> {
                    val response = result.data
                    fullTranscript.add(TranscriptEntry("Examiner", response.examinerLine))
                    _uiState.update {
                        it.copy(
                            examinerMessage = response.examinerLine,
                            isExaminerSpeaking = true // Start the speaking state
                        )
                    }
                    synthesizeAndPlaySingle(response.inputIds?.map { it.toLong() })
                }
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
        voskManager?.stopRecognition()
    }

    // --- Main Logic (Triggered by Vosk onResult) ---
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

        // --- THIS IS THE CORRECTED, UNIFIED STREAMING LOGIC ---
        _uiState.update { it.copy(isExaminerSpeaking = true, examinerMessage = "...") }
        accumulatedTextForStream = ""
        textStreamHasEnded = false
        audioPlaybackQueue.clear()
        isCurrentlyPlayingAudio = false
        AudioPlayer.stopPlayback()

        val request = ExamStepRequest(
            part = uiState.value.currentPart.ordinal,
            userInput = text,
            transcriptContext = fullTranscript.joinToString("\n") { "${it.speaker}: ${it.text}" }
        )

        chatRepository.getNextExamStepStream(request)
            .onEach { event ->
                when (event) {
                    is ExamEvent.TextChunk -> {
                        accumulatedTextForStream += event.text
                        _uiState.update { it.copy(examinerMessage = accumulatedTextForStream) }
                    }
                    is ExamEvent.InputIdsChunk -> {
                        if (event.ids.isNotEmpty()) {
                            ttsSynthesisRequestChannel.send(event.ids.map { it.toLong() })
                        }
                    }
                    is ExamEvent.StreamEnd -> {
                        textStreamHasEnded = true
                        fullTranscript.add(TranscriptEntry("Examiner", accumulatedTextForStream))
                        _uiState.update {
                            it.copy(
                                currentPart = ExamPart.entries[event.endData.next_part],
                                part2CueCard = event.endData.cue_card
                            )
                        }
                        isFinalQuestion = event.endData.is_final_question

                        if (audioPlaybackQueue.isEmpty() && !isCurrentlyPlayingAudio) {
                            handleExaminerSpeechFinished()
                        }
                    }
                    is ExamEvent.StreamError -> {
                        _uiState.update { it.copy(error = event.message, isExaminerSpeaking = false) }
                    }
                }
            }
            .catch { e ->
                Log.e("ExamViewModel", "Error collecting stream", e)
                _uiState.update { it.copy(error = e.message, isExaminerSpeaking = false) }
            }
            .launchIn(viewModelScope)
    }

    // --- Timers & Final Analysis ---
    private fun startPart2PrepTimer() {
        countdownTimer(60) {
            _uiState.update { it.copy(currentPart = ExamPart.PART_2_SPEAKING) }
            val prepTimeUpLine = "Your preparation time is up. Please start speaking now."
            fullTranscript.add(TranscriptEntry("Examiner", prepTimeUpLine))
            _uiState.update { it.copy(examinerMessage = prepTimeUpLine, isExaminerSpeaking = true) }
            synthesizeAndPlaySingle(emptyList()) // We can hardcode TTS for this simple line later
            startPart2SpeakingTimer()
        }
    }

    private fun startPart2SpeakingTimer() = countdownTimer(120) { stopUserSpeechRecognition() }

    private fun endExamAndAnalyze() {
        _uiState.update { it.copy(currentPart = ExamPart.FINISHED) }
        viewModelScope.launch {
            when (val result = chatRepository.analyzeFullExam(fullTranscript)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            finalResultId = result.data.resultId,
                            currentPart = ExamPart.ANALYSIS_COMPLETE
                        )
                    }
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

    // --- VOSK Implementation ---
    private fun initVoskModel() {
        StorageService.unpack(
            getApplication(), "model-en-us", "model",
            { model ->
                voskModel = model
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
            // This is the single point of entry for processing the user's speech
            processUserTranscription(finalText)
        }

        override fun onError(e: Exception) {
            _uiState.update { it.copy(isUserListening = false, error = "Vosk error: ${e.message}") }
        }

        override fun onFinalResult(hypothesis: String?) {}
        override fun onTimeout() { stopUserSpeechRecognition() }
    }

    // --- TTS & Audio Player System Implementation ---
    private fun initTtsAndAudioPlayer() {
        viewModelScope.launch { OnnxRuntimeManager.initialize(getApplication()) }
        observeTTSSynthesisRequests()
        observeAudioPlaybackRequests()
    }

    private fun observeTTSSynthesisRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            ttsSynthesisRequestChannel.consumeAsFlow().collect { sentenceInputIds ->
                try {
                    val audioWavByteArray = AudioPlayer.createAudioAndConvertToWav(sentenceInputIds, getApplication())
                    audioPlaybackDataChannel.send(audioWavByteArray)
                } catch (e: Exception) { Log.e("ExamTTS_Synth", "Error during TTS synthesis", e) }
            }
        }
    }

    private fun observeAudioPlaybackRequests() {
        viewModelScope.launch(Dispatchers.Main) {
            audioPlaybackDataChannel.receiveAsFlow().collect { audioData ->
                audioPlaybackQueue.offer(audioData)
                playNextAudioInQueue()
            }
        }
    }

    private fun playNextAudioInQueue() {
        if (isCurrentlyPlayingAudio || audioPlaybackQueue.isEmpty()) return

        isCurrentlyPlayingAudio = true
        val audioDataToPlay = audioPlaybackQueue.poll()

        if (audioDataToPlay != null) {
            AudioPlayer.playAudio(getApplication(), audioDataToPlay) {
                viewModelScope.launch(Dispatchers.Main) {
                    isCurrentlyPlayingAudio = false
                    playNextAudioInQueue()

                    if (audioPlaybackQueue.isEmpty() && textStreamHasEnded) {
                        handleExaminerSpeechFinished()
                    }
                }
            }
        } else {
            isCurrentlyPlayingAudio = false
        }
    }

    private fun synthesizeAndPlaySingle(inputIds: List<Long>?) {
        if (inputIds.isNullOrEmpty()) {
            handleExaminerSpeechFinished()
            return
        }
        viewModelScope.launch {
            val audioBytes = AudioPlayer.createAudioAndConvertToWav(inputIds, getApplication())
            AudioPlayer.playAudio(getApplication(), audioBytes) {
                handleExaminerSpeechFinished()
            }
        }
    }

    private fun handleExaminerSpeechFinished() {
        Log.d("ExamFlow", "All examiner audio has finished playing.")
        _uiState.update { it.copy(isExaminerSpeaking = false) }

        if (isFinalQuestion) {
            endExamAndAnalyze()
        } else if (_uiState.value.currentPart == ExamPart.PART_2_PREP) {
            startPart2PrepTimer()
        } else {
            startUserSpeechRecognition()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voskManager?.stopRecognition()
        timerJob?.cancel()
        ttsSynthesisRequestChannel.close()
        audioPlaybackDataChannel.close()
        AudioPlayer.release()
    }
}