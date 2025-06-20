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

// --- ViewModel Implementation (Final Corrected Version) ---

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
                    playInstructionAndWait(R.raw.multilevel_part1_intro)
                    startPart1_1()
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
        viewModelScope.launch {
            val content = _uiState.value.examContent?.part1_1 ?: return@launch
            val index = _uiState.value.part1_1_QuestionIndex
            if (index >= content.size) {
                startPart1_2()
                return@launch
            }

            val question = content[index]
            _uiState.update {
                it.copy(
                    stage = MultilevelExamStage.PART1_1_QUESTION,
                    currentQuestionText = question.questionText
                )
            }
            addTranscript("Examiner", question.questionText)

            AudioPlayer.playFromUrlAndWait(context, question.audioUrl)

            // This now correctly WAITS for the timer to finish
            startAnswerTimer(prepTime = 5, answerTime = 30)

            _uiState.update { it.copy(part1_1_QuestionIndex = index + 1) }
            startPart1_1() // Move to the next question
        }
    }

    private fun startPart1_2() {
        viewModelScope.launch {
            _uiState.update { it.copy(stage = MultilevelExamStage.PART1_2_INTRO) }
            playInstructionAndWait(R.raw.multilevel_part1_2_intro)
            processPart1_2_Question()
        }
    }

    private fun processPart1_2_Question() {
        viewModelScope.launch {
            val set = _uiState.value.examContent?.part1_2 ?: return@launch
            val index = _uiState.value.part1_2_QuestionIndex
            if (index >= set.questions.size) {
                startPart2()
                return@launch
            }

            val question = set.questions[index]
            val currentStage =
                if (index == 0) MultilevelExamStage.PART1_2_COMPARE else MultilevelExamStage.PART1_2_FOLLOWUP
            val prepTime = if (index == 0) 10 else 5
            val answerTime = if (index == 0) 45 else 30

            _uiState.update { it.copy(stage = currentStage, currentQuestionText = question.text) }
            addTranscript("Examiner", question.text)

            AudioPlayer.playFromUrlAndWait(context, question.audioUrl)

            // This now correctly WAITS for the timer to finish
            startAnswerTimer(prepTime = prepTime, answerTime = answerTime)

            _uiState.update { it.copy(part1_2_QuestionIndex = index + 1) }
            processPart1_2_Question()
        }
    }

    // --- Part 2 and 3 Logic ---
    // (No changes needed here, as they call the corrected timer functions)

    private fun startPart2() {
        viewModelScope.launch {
            _uiState.update { it.copy(stage = MultilevelExamStage.PART2_INTRO) }
            playInstructionAndWait(R.raw.multilevel_part2_intro)
            startPart2_Prep()
        }
    }

    private suspend fun startPart2_Prep() {
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
        AudioPlayer.playFromUrlAndWait(context, combinedAudioUrl)
        startTimer(duration = 60)
        startPart2_Speaking()
    }

    private suspend fun startPart2_Speaking() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART2_SPEAKING) }
        playStartSpeakingSound()
        recorder.start()
        _uiState.update { it.copy(isRecording = true) }
        startTimer(duration = 120)
        recorder.stop()
        _uiState.update { it.copy(isRecording = false) }
        startPart3()
    }

    private fun startPart3() {
        viewModelScope.launch {
            _uiState.update { it.copy(stage = MultilevelExamStage.PART3_INTRO) }
            playInstructionAndWait(R.raw.multilevel_part3_intro)
            startPart3_Prep()
        }
    }

    private suspend fun startPart3_Prep() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART3_PREP) }
        startTimer(duration = 60)
        startPart3_Speaking()
    }

    private suspend fun startPart3_Speaking() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART3_SPEAKING) }
        playStartSpeakingSound()
        recorder.start()
        _uiState.update { it.copy(isRecording = true) }
        startTimer(duration = 120)
        recorder.stop()
        _uiState.update { it.copy(isRecording = false) }
        concludeAndAnalyze()
    }

    // --- Analysis Logic (Unchanged) ---
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

    // --- *** THE CORE FIX IS HERE *** ---

    private suspend fun startAnswerTimer(prepTime: Int, answerTime: Int) {
        timerJob?.cancel()
        val newTimerJob = viewModelScope.launch {
            _uiState.update { it.copy(isRecording = false) }
            for (i in prepTime downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0) }
            playStartSpeakingSound()
            _uiState.update { it.copy(isRecording = true) }
            recorder.start()
            for (i in answerTime downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0, isRecording = false) }
            recorder.stop()
        }
        timerJob = newTimerJob
        newTimerJob.join() // This is the magic line that waits for the timer to complete
    }

    private suspend fun startTimer(duration: Int) {
        timerJob?.cancel()
        // This function also suspends now.
        val newTimerJob = viewModelScope.launch {
            for (i in duration downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0) }
        }
        timerJob = newTimerJob
        newTimerJob.join() // Also wait here
    }

    // --- Helper and Listener functions (Unchanged, but might need some suspend adjustments) ---

    private suspend fun playInstructionAndWait(@RawRes resId: Int) {
        try {
            AudioPlayer.playFromRawAndWait(context, resId)
            playStartSpeakingSound()
        } catch (e: Exception) {
            Log.e("ExamVM", "Instruction audio failed to play", e)
            _uiState.update { it.copy(error = "Audio playback failed. Please try again.") }
        }
    }

    private suspend fun playStartSpeakingSound() {
        try {
            AudioPlayer.playFromRawAndWait(context, R.raw.start_speaking_sound)
        } catch (e: Exception) {
            Log.e("ExamVM", "Start speaking sound failed", e)
        }
    }

    private fun addTranscript(speaker: String, text: String) {
        val newEntry = TranscriptEntry(speaker, text)
        _uiState.update { it.copy(transcript = it.transcript + newEntry) }
    }

    override fun onDataReceived(samples: FloatArray) {
        synchronized(audioDataBuffer) { audioDataBuffer.add(samples) }
    }

    override fun onRecordingStopped() {
        _uiState.update { it.copy(isRecording = false) }
        viewModelScope.launch {
            val transcribedText = "[User speech recorded. Replace with actual transcription.]"
            Log.d("ExamVM", "Recording stopped. Transcribed: '$transcribedText'")
            if (transcribedText.isNotBlank()) {
                addTranscript("User", transcribedText)
            }
            synchronized(audioDataBuffer) { audioDataBuffer.clear() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.stop()
        AudioPlayer.release()
        timerJob?.cancel()
    }
}