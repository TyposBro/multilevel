package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.R
import com.typosbro.multilevel.data.remote.models.AnalyzeRequest
import com.typosbro.multilevel.data.remote.models.MultilevelExamResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.repositories.DebugLogRepository
import com.typosbro.multilevel.data.repositories.ExamRepository
import com.typosbro.multilevel.features.vosk.VoskService
import com.typosbro.multilevel.utils.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Enums and UiState remain the same...
enum class PracticePart {
    FULL, P1_1, P1_2, P2, P3
}

enum class MultilevelExamStage {
    NOT_STARTED, LOADING, INTRO, PART1_1_QUESTION, PART1_2_INTRO, PART1_2_COMPARE,
    PART1_2_FOLLOWUP, PART2_INTRO, PART2_PREP, PART2_SPEAKING, PART3_INTRO,
    PART3_PREP, PART3_SPEAKING, ANALYZING, FINISHED_ERROR
}


object MULTILEVEL_TIMEOUTS {
    const val PART1_1_PREP = 5
    const val PART1_1_ANSWER = 30
    const val PART1_2_PREP_FIRST = 10
    const val PART1_2_PREP_FOLLOWUP = 5
    const val PART1_2_ANSWER_FIRST = 45
    const val PART1_2_ANSWER_FOLLOWUP = 30
    const val PART2_PREP = 60
    const val PART2_SPEAKING = 120
    const val PART3_PREP = 60
    const val PART3_SPEAKING = 120
}

data class MultilevelUiState(
    val stage: MultilevelExamStage = MultilevelExamStage.NOT_STARTED,
    val examContent: MultilevelExamResponse? = null,
    val currentQuestionText: String? = null,
    val part1_1_QuestionIndex: Int = 0,
    val part1_2_QuestionIndex: Int = 0,
    val timerValue: Int = 0,
    val isRecording: Boolean = false,
    val transcript: List<TranscriptEntry> = emptyList(),
    val error: String? = null,
    val finalResultId: String? = null
)


@HiltViewModel
class MultilevelExamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExamRepository,
    private val voskService: VoskService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultilevelUiState())
    val uiState = _uiState.asStateFlow()

    private val practicePart: PracticePart =
        savedStateHandle.get<String>("practicePart")?.let {
            try {
                PracticePart.valueOf(it)
            } catch (e: IllegalArgumentException) {
                Log.e("ExamVM", "Invalid practice part argument: $it. Defaulting to FULL.")
                PracticePart.FULL
            }
        } ?: PracticePart.FULL

    private var examJob: Job? = null

    private fun updateState(caller: String, transform: (MultilevelUiState) -> MultilevelUiState) {
        Log.i("STATE_UPDATE", "Request from [$caller]")
        _uiState.update(transform)
    }

    init {
        _uiState.onEach { newState ->
            Log.d("STATE_DEBUG", "New State -> $newState")
        }.launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            voskService.initialize()
        }

        voskService.resultListener = { text ->
            addTranscript(TranscriptEntry("User", text))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun startExam() {
        if (uiState.value.stage != MultilevelExamStage.NOT_STARTED) return
        updateState("startExam: Loading") { it.copy(stage = MultilevelExamStage.LOADING) }

        examJob = viewModelScope.launch {
            val result = repository.getNewExam()
            if (result !is RepositoryResult.Success) {
                val errorMessage =
                    (result as? RepositoryResult.Error)?.message ?: "Unknown error loading exam"
                updateState("startExam: Error") {
                    it.copy(stage = MultilevelExamStage.FINISHED_ERROR, error = errorMessage)
                }
                return@launch
            }
            updateState("startExam: Success") { it.copy(examContent = result.data) }

            // *** REMOVED VOSK START FROM HERE ***

            when (practicePart) {
                PracticePart.FULL -> {
                    executePart1_1()
                    if (!isActive) return@launch
                    executePart1_2()
                    if (!isActive) return@launch
                    executePart2()
                    if (!isActive) return@launch
                    executePart3()
                    if (!isActive) return@launch
                    concludeAndAnalyze()
                }

                PracticePart.P1_1 -> {
                    executePart1_1(); if (isActive) concludeAndAnalyze()
                }

                PracticePart.P1_2 -> {
                    executePart1_2(); if (isActive) concludeAndAnalyze()
                }

                PracticePart.P2 -> {
                    executePart2(); if (isActive) concludeAndAnalyze()
                }

                PracticePart.P3 -> {
                    executePart3(); if (isActive) concludeAndAnalyze()
                }
            }
        }
    }

    private suspend fun executePart1_1() {
        updateState("executePart1_1: Intro") { it.copy(stage = MultilevelExamStage.INTRO) }
        playInstructionAndWait(R.raw.multilevel_part1_intro)
        val content = _uiState.value.examContent?.part1_1 ?: return
        for ((index, question) in content.withIndex()) {
            if (!viewModelScope.isActive) return
            updateState("executePart1_1: Question ${index + 1}") {
                it.copy(
                    stage = MultilevelExamStage.PART1_1_QUESTION,
                    currentQuestionText = question.questionText,
                    part1_1_QuestionIndex = index
                )
            }
            addTranscript(TranscriptEntry("Examiner", question.questionText))
            AudioPlayer.playFromUrlAndWait(context, question.audioUrl)
            startAnswerTimer(MULTILEVEL_TIMEOUTS.PART1_1_PREP, MULTILEVEL_TIMEOUTS.PART1_1_ANSWER)
        }
    }

    private suspend fun executePart1_2() {
        updateState("executePart1_2: Intro") { it.copy(stage = MultilevelExamStage.PART1_2_INTRO) }
        playInstructionAndWait(R.raw.multilevel_part1_2_intro)
        val set = _uiState.value.examContent?.part1_2 ?: return
        for ((index, question) in set.questions.withIndex()) {
            if (!viewModelScope.isActive) return
            val (currentStage, prepTime, answerTime) = if (index == 0) {
                Triple(
                    MultilevelExamStage.PART1_2_COMPARE,
                    MULTILEVEL_TIMEOUTS.PART1_2_PREP_FIRST,
                    MULTILEVEL_TIMEOUTS.PART1_2_ANSWER_FIRST
                )
            } else {
                Triple(
                    MultilevelExamStage.PART1_2_FOLLOWUP,
                    MULTILEVEL_TIMEOUTS.PART1_2_PREP_FOLLOWUP,
                    MULTILEVEL_TIMEOUTS.PART1_2_ANSWER_FOLLOWUP
                )
            }
            updateState("executePart1_2: Question ${index + 1}") {
                it.copy(
                    stage = currentStage,
                    currentQuestionText = question.text,
                    part1_2_QuestionIndex = index
                )
            }
            addTranscript(TranscriptEntry("Examiner", question.text))
            AudioPlayer.playFromUrlAndWait(context, question.audioUrl)
            startAnswerTimer(prepTime, answerTime)
        }
    }

    private suspend fun executePart2() {
        updateState("executePart2: Intro") { it.copy(stage = MultilevelExamStage.PART2_INTRO) }
        playInstructionAndWait(R.raw.multilevel_part2_intro)
        val set = _uiState.value.examContent?.part2 ?: return
        val fullQuestionText = set.questions.joinToString("\n") { it.text }
        updateState("executePart2: Prep") {
            it.copy(stage = MultilevelExamStage.PART2_PREP, currentQuestionText = fullQuestionText)
        }
        addTranscript(TranscriptEntry("Examiner", fullQuestionText))
        AudioPlayer.playFromUrlAndWait(context, set.questions.firstOrNull()?.audioUrl ?: "")
        startMonologueTimer(
            MULTILEVEL_TIMEOUTS.PART2_PREP,
            MULTILEVEL_TIMEOUTS.PART2_SPEAKING,
            MultilevelExamStage.PART2_SPEAKING
        )
    }

    private suspend fun executePart3() {
        updateState("executePart3: Intro") { it.copy(stage = MultilevelExamStage.PART3_INTRO) }
        playInstructionAndWait(R.raw.multilevel_part3_intro)
        updateState("executePart3: Prep") { it.copy(stage = MultilevelExamStage.PART3_PREP) }
        startMonologueTimer(
            MULTILEVEL_TIMEOUTS.PART3_PREP,
            MULTILEVEL_TIMEOUTS.PART3_SPEAKING,
            MultilevelExamStage.PART3_SPEAKING
        )
    }

    private suspend fun startAnswerTimer(prepTime: Int, answerTime: Int) {
        updateState("startAnswerTimer: Prep phase") { it.copy(isRecording = false) }
        // Service is not running, so no need to pause
        startTimer(prepTime)
        if (!viewModelScope.isActive) return

        updateState("startAnswerTimer: Answer phase") { it.copy(isRecording = true) }
        playStartSpeakingSound()
        if (!voskService.startRecognition()) {
            updateState("startAnswerTimer: Error") {
                it.copy(
                    stage = MultilevelExamStage.FINISHED_ERROR,
                    error = "Could not start recognition."
                )
            }
            return
        }

        startTimer(answerTime)
        if (!viewModelScope.isActive) return

        updateState("startAnswerTimer: Stopping recording") { it.copy(isRecording = false) }
        voskService.stopRecognition()
    }

    private suspend fun startMonologueTimer(
        prepTime: Int,
        speakTime: Int,
        speakingStage: MultilevelExamStage
    ) {
        updateState("startMonologueTimer: Prep") { it.copy(isRecording = false) }
        // Service is not running, so no need to pause
        startTimer(prepTime)
        if (!viewModelScope.isActive) return

        updateState("startMonologueTimer: Speaking") {
            it.copy(
                stage = speakingStage,
                isRecording = true
            )
        }
        playStartSpeakingSound()
        if (!voskService.startRecognition()) {
            updateState("startMonologueTimer: Error") {
                it.copy(
                    stage = MultilevelExamStage.FINISHED_ERROR,
                    error = "Could not start recognition."
                )
            }
            return
        }

        startTimer(speakTime)
        if (!viewModelScope.isActive) return

        updateState("startMonologueTimer: Stopping") { it.copy(isRecording = false) }
        voskService.stopRecognition()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun concludeAndAnalyze() {
        // Service is already stopped after the last speaking segment.
        updateState("concludeAndAnalyze: Analyzing") { it.copy(stage = MultilevelExamStage.ANALYZING) }

        // We add a small delay to ensure the final result from Vosk has time to be processed.
        viewModelScope.launch {
            delay(500)

            val logDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
            if (logDir != null) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val finalLogFile = File(logDir, "full_exam_transcript_$timestamp.wav")
                DebugLogRepository.saveTranscript(context, finalLogFile, uiState.value.transcript)
            }

            val examContent = uiState.value.examContent ?: return@launch
            val partString = if (practicePart == PracticePart.FULL) "FULL" else practicePart.name
            val request = AnalyzeRequest(uiState.value.transcript, examContent, partString)

            when (val result = repository.analyzeExam(request)) {
                is RepositoryResult.Success -> {
                    updateState("concludeAndAnalyze: Success") { it.copy(finalResultId = result.data) }
                }

                is RepositoryResult.Error -> {
                    updateState("concludeAndAnalyze: Error") {
                        it.copy(stage = MultilevelExamStage.FINISHED_ERROR, error = result.message)
                    }
                }
            }
        }
    }

    // startTimer, playInstructionAndWait, etc. remain the same...
    private suspend fun startTimer(duration: Int) {
        val job = viewModelScope.launch {
            for (i in duration downTo 1) {
                if (!isActive) return@launch
                updateState("startTimer: countdown") { it.copy(timerValue = i) }
                delay(1000)
            }
            updateState("startTimer: finished") { it.copy(timerValue = 0) }
        }
        job.join()
    }

    private suspend fun playInstructionAndWait(@RawRes resId: Int) {
        try {
            AudioPlayer.playFromRawAndWait(context, resId)
        } catch (e: Exception) {
            Log.e("ExamVM", "Instruction audio failed to play", e)
            updateState("playInstructionAndWait: Error") { it.copy(error = "Audio playback failed. Please try again.") }
        }
    }

    private suspend fun playStartSpeakingSound() {
        try {
            AudioPlayer.playFromRawAndWait(context, R.raw.start_speaking_sound)
        } catch (e: Exception) {
            Log.e("ExamVM", "Start speaking sound failed", e)
        }
    }

    private fun addTranscript(newEntry: TranscriptEntry) {
        _uiState.update { currentState ->
            val currentTranscript = currentState.transcript
            val lastEntry = currentTranscript.lastOrNull()

            val updatedTranscript =
                if (newEntry.speaker == "User" && lastEntry?.speaker == "User") {
                    val updatedText = "${lastEntry.text} ${newEntry.text}".trim()
                    currentTranscript.dropLast(1) + lastEntry.copy(text = updatedText)
                } else {
                    currentTranscript + newEntry
                }
            currentState.copy(transcript = updatedTranscript)
        }
    }

    private fun getAssetFile(assetName: String): File {
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { it.copyTo(file.outputStream()) }
        }
        return file
    }

    fun stopExam() {
        examJob?.cancel()
        voskService.release()
        AudioPlayer.release()
        updateState("stopExam: Force stop") {
            it.copy(
                stage = MultilevelExamStage.FINISHED_ERROR,
                error = "The exam was interrupted.",
                isRecording = false,
                timerValue = 0
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopExam()
    }
}