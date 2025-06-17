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
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

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

@HiltViewModel
class ExamViewModel @Inject constructor(
    application: Application,
    private val chatRepository: ChatRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState = _uiState.asStateFlow()

    private val fullTranscript = mutableListOf<TranscriptEntry>()
    private var isFinalQuestion = false
    private var questionTimerJob: Job? = null
    private var textStreamHasEnded = false

    private var voskModel: Model? = null
    private var voskManager: VoskRecognitionManager? = null
    private var lastPartialResult = ""

    private var accumulatedTextForStream: String = ""
    private val ttsSynthesisRequestChannel = Channel<List<Long>>(Channel.UNLIMITED)
    private val audioPlaybackDataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val audioPlaybackQueue: Queue<ByteArray> = LinkedList()
    private var isCurrentlyPlayingAudio = false
    private val pendingAudioTasks = AtomicInteger(0)

    // --- Frontend Exam Structure Logic ---
    private var questionCounter = 0
    private val part1QuestionCount = 4 // IELTS Part 1: 4-5 questions
    private val part3QuestionCount = 5 // IELTS Part 3: 5-6 questions

    init {
        initVoskModel()
        initTtsAndAudioPlayer()
    }

    private fun getTimeLimitForPart(part: ExamPart): Int? {
        return when (part) {
            ExamPart.PART_1 -> 45
            ExamPart.PART_2_SPEAKING -> 120
            ExamPart.PART_3 -> 60
            else -> null
        }
    }

    fun startExam() {
        questionCounter = 0
        _uiState.update { it.copy(currentPart = ExamPart.PART_1, error = null) }
        fullTranscript.clear()
        isFinalQuestion = false
        textStreamHasEnded = false
        pendingAudioTasks.set(0)
        questionTimerJob?.cancel()

        viewModelScope.launch {
            when (val result = chatRepository.getInitialExamQuestion()) {
                is Result.Success -> {
                    val response = result.data
                    fullTranscript.add(TranscriptEntry("Examiner", response.examinerLine))
                    _uiState.update {
                        it.copy(
                            examinerMessage = response.examinerLine,
                            isExaminerSpeaking = true
                        )
                    }
                    synthesizeAndPlaySingle(response.inputIds?.map { it.toLong() }) {
                        handleExaminerSpeechFinished()
                    }
                }
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun startUserSpeechRecognition() {
        if (!uiState.value.isModelReady || voskManager == null || uiState.value.isUserListening || uiState.value.isExaminerSpeaking) return
        _uiState.update { it.copy(isUserListening = true, partialTranscription = "") }
        lastPartialResult = ""
        voskManager?.startMicrophoneRecognition()
    }

    fun stopUserSpeechRecognition() {
        if (!uiState.value.isUserListening) return
        questionTimerJob?.cancel()
        _uiState.update { it.copy(isUserListening = false, timerValue = 0) }
        voskManager?.stopRecognition()
        processUserTranscription(lastPartialResult)
    }

    private fun startQuestionTimer(timeLimit: Int?) {
        questionTimerJob?.cancel()
        _uiState.update { it.copy(timerValue = 0) }
        if (timeLimit == null || timeLimit <= 0) return

        Log.d("ExamFlow", "Starting timer for $timeLimit seconds.")
        questionTimerJob = viewModelScope.launch {
            _uiState.update { it.copy(timerValue = timeLimit) }
            for (i in timeLimit downTo 1) {
                if (!isActive) return@launch
                delay(1000)
                _uiState.update { it.copy(timerValue = it.timerValue - 1) }
            }
            Log.d("ExamFlow", "Time limit reached. Auto-stopping recognition.")
            stopUserSpeechRecognition()
        }
    }

    private fun processUserTranscription(text: String) {
        _uiState.update { it.copy(timerValue = 0) }

        if (text.isBlank()) {
            if (fullTranscript.isNotEmpty()) handleExaminerSpeechFinished()
            return
        }

        val currentPart = uiState.value.currentPart
        if (currentPart == ExamPart.PART_1 || currentPart == ExamPart.PART_3) {
            questionCounter++
        }
        fullTranscript.add(TranscriptEntry("User", text))

        if (isFinalQuestion) {
            endExamAndAnalyze()
            return
        }

        _uiState.update { it.copy(isExaminerSpeaking = true, examinerMessage = "...") }
        accumulatedTextForStream = ""
        textStreamHasEnded = false
        audioPlaybackQueue.clear()
        isCurrentlyPlayingAudio = false
        pendingAudioTasks.set(0)
        AudioPlayer.stopPlayback()

        val request = ExamStepRequest(
            part = uiState.value.currentPart.ordinal,
            userInput = text,
            transcriptContext = fullTranscript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCounter
        )

        chatRepository.getNextExamStepStream(request)
            .onEach { event ->
                when (event) {
                    is ExamEvent.StreamEnd -> {
                        textStreamHasEnded = true
                        fullTranscript.add(TranscriptEntry("Examiner", accumulatedTextForStream))

                        val newPart = ExamPart.entries[event.endData.next_part]
                        var finalQuestionFlag = event.endData.is_final_question

                        if (currentPart == ExamPart.PART_1 && questionCounter < part1QuestionCount) {
                            Log.w("ExamLogic", "Overriding is_final_question flag in Part 1. Question $questionCounter/$part1QuestionCount")
                            finalQuestionFlag = false
                        }
                        if (currentPart == ExamPart.PART_3 && questionCounter < part3QuestionCount) {
                            Log.w("ExamLogic", "Overriding is_final_question flag in Part 3. Question $questionCounter/$part3QuestionCount")
                            finalQuestionFlag = false
                        }

                        if (newPart != currentPart) {
                            Log.d("ExamLogic", "Transitioning from $currentPart to $newPart. Resetting question counter.")
                            questionCounter = 0
                        }

                        isFinalQuestion = finalQuestionFlag
                        _uiState.update { it.copy(currentPart = newPart, part2CueCard = event.endData.cue_card) }
                        checkIfTurnIsComplete()
                    }
                    is ExamEvent.TextChunk -> {
                        accumulatedTextForStream += event.text
                        _uiState.update { it.copy(examinerMessage = accumulatedTextForStream) }
                    }
                    is ExamEvent.InputIdsChunk -> {
                        if (event.ids.isNotEmpty()) {
                            pendingAudioTasks.incrementAndGet()
                            ttsSynthesisRequestChannel.send(event.ids.map { it.toLong() })
                        }
                    }
                    is ExamEvent.StreamError -> {
                        _uiState.update { it.copy(error = event.message, isExaminerSpeaking = false) }
                        textStreamHasEnded = false
                        pendingAudioTasks.set(0)
                    }
                }
            }
            .catch { e ->
                Log.e("ExamViewModel", "Error collecting stream", e)
                _uiState.update { it.copy(error = e.message, isExaminerSpeaking = false) }
                textStreamHasEnded = false
                pendingAudioTasks.set(0)
            }
            .launchIn(viewModelScope)
    }

    private fun checkIfTurnIsComplete() {
        if (textStreamHasEnded && pendingAudioTasks.get() == 0) {
            handleExaminerSpeechFinished()
        }
    }

    private fun handleExaminerSpeechFinished() {
        if (!uiState.value.isExaminerSpeaking && !textStreamHasEnded) return
        textStreamHasEnded = false

        Log.d("ExamFlow", "Examiner speech finished. Ready for user.")
        _uiState.update { it.copy(isExaminerSpeaking = false, timerValue = 0) }

        if (isFinalQuestion) {
            endExamAndAnalyze()
            return
        }

        val timeLimit = getTimeLimitForPart(uiState.value.currentPart)

        when (uiState.value.currentPart) {
            ExamPart.PART_2_PREP -> startPart2PrepTimer(60)
            else -> {
                startUserSpeechRecognition()
                startQuestionTimer(timeLimit)
            }
        }
    }

    private fun startPart2PrepTimer(prepTime: Int) {
        questionTimerJob?.cancel()
        questionTimerJob = viewModelScope.launch {
            _uiState.update { it.copy(timerValue = prepTime) }
            for (i in prepTime downTo 1) {
                if (!isActive) return@launch
                delay(1000)
                _uiState.update { it.copy(timerValue = it.timerValue - 1) }
            }

            _uiState.update { it.copy(currentPart = ExamPart.PART_2_SPEAKING, timerValue = 0) }
            val prepTimeUpLine = "Your preparation time is up. Please start speaking now."
            fullTranscript.add(TranscriptEntry("Examiner", prepTimeUpLine))
            _uiState.update { it.copy(examinerMessage = prepTimeUpLine, isExaminerSpeaking = true) }

            synthesizeAndPlaySingle(null) {
                handleExaminerSpeechFinished()
            }
        }
    }

    private fun endExamAndAnalyze() {
        _uiState.update { it.copy(currentPart = ExamPart.FINISHED) }
        viewModelScope.launch {
            when (val result = chatRepository.analyzeFullExam(fullTranscript)) {
                is Result.Success -> _uiState.update { it.copy(finalResultId = result.data.resultId, currentPart = ExamPart.ANALYSIS_COMPLETE) }
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            val partialText = JSONObject(hypothesis).optString("partial", "")
            if (partialText.isNotBlank() && partialText != lastPartialResult) {
                lastPartialResult = partialText
                _uiState.update { it.copy(partialTranscription = partialText) }
            }
        }
        override fun onResult(hypothesis: String) {
            val finalText = JSONObject(hypothesis).optString("text", "")
            if (finalText.isNotBlank()) lastPartialResult = finalText
        }
        override fun onError(e: Exception) { _uiState.update { it.copy(isUserListening = false, error = "Vosk error: ${e.message}") } }
        override fun onFinalResult(hypothesis: String?) {
            val finalText = JSONObject(hypothesis).optString("text", "")
            if (finalText.isNotBlank()) lastPartialResult = finalText
        }
        override fun onTimeout() { Log.d("ExamFlow", "Vosk internal timeout ignored.") }
    }

    private fun initVoskModel() {
        StorageService.unpack(getApplication(), "model-en-us", "model",
            { model ->
                this.voskModel = model
                voskModel?.let { voskManager = VoskRecognitionManager(getApplication(), it, recognitionListener); _uiState.update { s -> s.copy(isModelReady = true) } }
            },
            { e -> _uiState.update { it.copy(error = "Failed to init model: ${e.message}") } }
        )
    }

    private fun initTtsAndAudioPlayer() {
        viewModelScope.launch { OnnxRuntimeManager.initialize(getApplication()) }
        observeTTSSynthesisRequests()
        observeAudioPlaybackRequests()
    }

    private fun observeTTSSynthesisRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            ttsSynthesisRequestChannel.consumeAsFlow().collect { ids ->
                try {
                    audioPlaybackDataChannel.send(AudioPlayer.createAudioAndConvertToWav(ids, getApplication()))
                } catch (e: Exception) {
                    Log.e("ExamTTS_Synth", "Error during TTS synthesis", e)
                    pendingAudioTasks.decrementAndGet()
                    checkIfTurnIsComplete()
                }
            }
        }
    }

    private fun observeAudioPlaybackRequests() {
        viewModelScope.launch(Dispatchers.Main) {
            audioPlaybackDataChannel.receiveAsFlow().collect { data ->
                audioPlaybackQueue.offer(data)
                if (!isCurrentlyPlayingAudio) playNextAudioInQueue()
            }
        }
    }

    private fun playNextAudioInQueue() {
        if (isCurrentlyPlayingAudio || audioPlaybackQueue.isEmpty()) return
        isCurrentlyPlayingAudio = true
        AudioPlayer.playAudio(getApplication(), audioPlaybackQueue.poll()!!) {
            viewModelScope.launch(Dispatchers.Main) {
                isCurrentlyPlayingAudio = false
                pendingAudioTasks.decrementAndGet()
                Log.d("ExamFlow", "Audio chunk finished. Pending: ${pendingAudioTasks.get()}")
                playNextAudioInQueue()
                checkIfTurnIsComplete()
            }
        }
    }

    private fun synthesizeAndPlaySingle(inputIds: List<Long>?, onFinish: () -> Unit) {
        if (inputIds.isNullOrEmpty()) { viewModelScope.launch { delay(500); onFinish() }; return }
        viewModelScope.launch { AudioPlayer.playAudio(getApplication(), AudioPlayer.createAudioAndConvertToWav(inputIds, getApplication()), onFinish) }
    }

    override fun onCleared() {
        super.onCleared()
        voskManager?.stopRecognition()
        questionTimerJob?.cancel()
        ttsSynthesisRequestChannel.close()
        audioPlaybackDataChannel.close()
        AudioPlayer.release()
    }
}