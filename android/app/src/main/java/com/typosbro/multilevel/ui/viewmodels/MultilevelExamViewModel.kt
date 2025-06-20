package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.R
import com.typosbro.multilevel.data.remote.models.ExamContentIds
import com.typosbro.multilevel.data.remote.models.MultilevelAnalyzeRequest
import com.typosbro.multilevel.data.remote.models.MultilevelExamResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.repositories.MultilevelExamRepository
import com.typosbro.multilevel.features.whisper.Recorder
import com.typosbro.multilevel.utils.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- State Definitions (Unchanged) ---
enum class MultilevelExamStage {
    NOT_STARTED,
    LOADING,
    INTRO,
    PART1_1_QUESTION,
    PART1_2_INTRO,
    PART1_2_COMPARE,
    PART1_2_FOLLOWUP,
    PART2_INTRO,
    PART2_PREP,
    PART2_SPEAKING,
    PART3_INTRO,
    PART3_PREP,
    PART3_SPEAKING,
    ANALYZING,
    FINISHED_ERROR
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

// --- ViewModel Implementation ---

@HiltViewModel
class MultilevelExamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MultilevelExamRepository,
) : ViewModel(), Recorder.RecorderListener {

    private val _uiState = MutableStateFlow(MultilevelUiState())
    val uiState = _uiState.asStateFlow()

    private val recorder: Recorder = Recorder(context, this)
    private var timerJob: Job? = null
    private val audioDataBuffer = mutableListOf<FloatArray>()

    fun startExam() {
        if (uiState.value.stage != MultilevelExamStage.NOT_STARTED) return
        _uiState.update { it.copy(stage = MultilevelExamStage.LOADING) }

        viewModelScope.launch {
            when (val result = repository.getNewExam()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            examContent = result.data,
                            stage = MultilevelExamStage.INTRO
                        )
                    }
                    playInstruction(R.raw.multilevel_part1_intro) {
                        startPart1_1()
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            stage = MultilevelExamStage.FINISHED_ERROR,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    private fun startPart1_1() {
        val content = _uiState.value.examContent?.part1_1 ?: return
        val index = _uiState.value.part1_1_QuestionIndex
        if (index >= content.size) {
            startPart1_2()
            return
        }

        val question = content[index]
        _uiState.update {
            it.copy(
                stage = MultilevelExamStage.PART1_1_QUESTION,
                currentQuestionText = question.questionText
            )
        }
        addTranscript("Examiner", question.questionText)

        AudioPlayer.playFromUrl(context, question.audioUrl) {
            startAnswerTimer(prepTime = 5, answerTime = 30) {
                _uiState.update { it.copy(part1_1_QuestionIndex = index + 1) }
                startPart1_1()
            }
        }
    }

    private fun startPart1_2() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART1_2_INTRO) }
        playInstruction(R.raw.multilevel_part1_2_intro) {
            processPart1_2_Question()
        }
    }

    private fun processPart1_2_Question() {
        val set = _uiState.value.examContent?.part1_2 ?: return
        val index = _uiState.value.part1_2_QuestionIndex
        if (index >= set.questions.size) {
            startPart2()
            return
        }

        val question = set.questions[index]
        val currentStage =
            if (index == 0) MultilevelExamStage.PART1_2_COMPARE else MultilevelExamStage.PART1_2_FOLLOWUP
        val prepTime = if (index == 0) 10 else 5
        val answerTime = if (index == 0) 45 else 30

        _uiState.update { it.copy(stage = currentStage, currentQuestionText = question.text) }
        addTranscript("Examiner", question.text)

        AudioPlayer.playFromUrl(context, question.audioUrl) {
            startAnswerTimer(prepTime = prepTime, answerTime = answerTime) {
                _uiState.update { it.copy(part1_2_QuestionIndex = index + 1) }
                processPart1_2_Question()
            }
        }
    }

    private fun startPart2() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART2_INTRO) }
        playInstruction(R.raw.multilevel_part2_intro) {
            startPart2_Prep()
        }
    }

    private fun startPart2_Prep() {
        val set = _uiState.value.examContent?.part2 ?: return
        val fullQuestionText = set.questions.joinToString("\n") { it.text }

        _uiState.update {
            it.copy(
                stage = MultilevelExamStage.PART2_PREP,
                currentQuestionText = fullQuestionText
            )
        }
        addTranscript("Examiner", fullQuestionText)

        val combinedAudioUrl = set.questions.firstOrNull()?.audioUrl ?: ""
        AudioPlayer.playFromUrl(context, combinedAudioUrl) {
            startTimer(duration = 60, stageOnFinish = MultilevelExamStage.PART2_SPEAKING) {
                startPart2_Speaking()
            }
        }
    }

    private fun startPart2_Speaking() {
        playInstruction(R.raw.start_speaking_sound) {
            recorder.start()
            _uiState.update { it.copy(isRecording = true) }
            startTimer(duration = 120, stageOnFinish = MultilevelExamStage.PART3_INTRO) {
                recorder.stop()
                startPart3()
            }
        }
    }

    private fun startPart3() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART3_INTRO) }
        playInstruction(R.raw.multilevel_part3_intro) {
            startPart3_Prep()
        }
    }

    private fun startPart3_Prep() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART3_PREP) }
        startTimer(duration = 60, stageOnFinish = MultilevelExamStage.PART3_SPEAKING) {
            startPart3_Speaking()
        }
    }

    private fun startPart3_Speaking() {
        playInstruction(R.raw.start_speaking_sound) {
            recorder.start()
            _uiState.update { it.copy(isRecording = true) }
            startTimer(duration = 120, stageOnFinish = MultilevelExamStage.ANALYZING) {
                recorder.stop()
                concludeAndAnalyze()
            }
        }
    }

    private fun concludeAndAnalyze() {
        _uiState.update { it.copy(stage = MultilevelExamStage.ANALYZING) }
        viewModelScope.launch {
            val examContent = uiState.value.examContent ?: return@launch
            val contentIds = ExamContentIds(
                part1_1 = examContent.part1_1.map { it.id },
                part1_2 = examContent.part1_2.id,
                part2 = examContent.part2.id,
                part3 = examContent.part3.id
            )
            val request = MultilevelAnalyzeRequest(uiState.value.transcript, contentIds)

            when (val result = repository.analyzeExam(request)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(finalResultId = result.data.resultId) }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            stage = MultilevelExamStage.FINISHED_ERROR,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    // --- CORRECTED FUNCTION ---
    private fun startAnswerTimer(prepTime: Int, answerTime: Int, onFinished: () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch { // Launch a single coroutine to manage the whole sequence
            // 1. Prep countdown (this is a suspend block)
            for (i in prepTime downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0) }

            // 2. Play the start sound. The onCompletion lambda does not need to be a coroutine.
            playInstruction(R.raw.start_speaking_sound) {
                // This part is NOT a coroutine, so we just update state and start the recorder.
                // The timer logic continues in the parent coroutine.
                _uiState.update { it.copy(isRecording = true) }
                recorder.start()
            }

            // 3. Answer countdown (this is also a suspend block)
            for (i in answerTime downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }

            // 4. Timer finished: stop recorder and call the onFinished lambda
            recorder.stop()
            // onRecordingStopped will be called, which handles transcription.

            // Allow a brief moment for transcription to potentially finish before moving on
            delay(200)
            onFinished()
        }
    }

    private fun startTimer(
        duration: Int,
        stageOnFinish: MultilevelExamStage,
        onFinished: () -> Unit
    ) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (i in duration downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0, stage = stageOnFinish) }
            onFinished()
        }
    }

    private fun playInstruction(@RawRes resId: Int, onCompletion: () -> Unit) {
        AudioPlayer.playFromRaw(context, resId, onCompletion)
    }

    private fun addTranscript(speaker: String, text: String) {
        val newEntry = TranscriptEntry(speaker, text)
        _uiState.update { it.copy(transcript = it.transcript + newEntry) }
    }

    override fun onDataReceived(samples: FloatArray) {
        synchronized(audioDataBuffer) {
            audioDataBuffer.add(samples)
        }
    }

    override fun onRecordingStopped() {
        _uiState.update { it.copy(isRecording = false) }
        viewModelScope.launch {
            // Your Whisper transcription logic goes here.
            val transcribedText = "[User speech recorded. Replace with actual transcription.]"
            Log.d("ExamVM", "Recording stopped. Transcribed: '$transcribedText'")
            if (transcribedText.isNotBlank()) {
                addTranscript("User", transcribedText)
            }

            synchronized(audioDataBuffer) {
                audioDataBuffer.clear()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.stop()
        AudioPlayer.release()
        timerJob?.cancel()
    }
}