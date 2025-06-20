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
import com.typosbro.multilevel.features.whisper.engine.WhisperEngineNative
import com.typosbro.multilevel.utils.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

// --- State Definitions (Unchanged) ---
enum class MultilevelExamStage {
    NOT_STARTED, LOADING, INTRO, PART1_1_QUESTION, PART1_2_INTRO, PART1_2_COMPARE,
    PART1_2_FOLLOWUP, PART2_INTRO, PART2_PREP, PART2_SPEAKING, PART3_INTRO,
    PART3_PREP, PART3_SPEAKING, ANALYZING, FINISHED_ERROR
}

object MULTILEVEL_TIMEOUTS {
    // Using shorter times for easier debugging
    const val PART1_1_PREP = 3
    const val PART1_1_ANSWER = 5
    const val PART1_2_PREP_FIRST = 3
    const val PART1_2_PREP_FOLLOWUP = 3
    const val PART1_2_ANSWER_FIRST = 5
    const val PART1_2_ANSWER_FOLLOWUP = 5
    const val PART2_PREP = 5
    const val PART2_SPEAKING = 7
    const val PART3_PREP = 5
    const val PART3_SPEAKING = 7
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
    private val repository: MultilevelExamRepository,
) : ViewModel(), Recorder.RecorderListener {

    private val _uiState = MutableStateFlow(MultilevelUiState())
    val uiState = _uiState.asStateFlow()

    private var whisperEngine: WhisperEngineNative? = null
    private val recorder: Recorder = Recorder(context, this)
    private var timerJob: Job? = null
    private val audioDataBuffer = mutableListOf<FloatArray>()

    // This continuation bridges the callback-based Recorder with coroutines.
    private var transcriptionContinuation: Continuation<String>? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            whisperEngine = WhisperEngineNative(context).also {
                val modelFile = getAssetFile("whisper-tiny.en.tflite")
                it.initialize(modelFile.absolutePath, "", false)
            }
        }
    }

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

            // This function now correctly waits for prep, recording, and transcription.
            startAnswerTimer(
                prepTime = MULTILEVEL_TIMEOUTS.PART1_1_PREP,
                answerTime = MULTILEVEL_TIMEOUTS.PART1_1_ANSWER
            )

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
            val prepTime =
                if (index == 0) MULTILEVEL_TIMEOUTS.PART1_2_PREP_FIRST else MULTILEVEL_TIMEOUTS.PART1_2_PREP_FOLLOWUP
            val answerTime =
                if (index == 0) MULTILEVEL_TIMEOUTS.PART1_2_ANSWER_FIRST else MULTILEVEL_TIMEOUTS.PART1_2_ANSWER_FOLLOWUP

            _uiState.update { it.copy(stage = currentStage, currentQuestionText = question.text) }
            addTranscript("Examiner", question.text)
            AudioPlayer.playFromUrlAndWait(context, question.audioUrl)

            startAnswerTimer(prepTime = prepTime, answerTime = answerTime)

            _uiState.update { it.copy(part1_2_QuestionIndex = index + 1) }
            processPart1_2_Question()
        }
    }

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
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART2_PREP)
        startPart2_Speaking()
    }

    private suspend fun startPart2_Speaking() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART2_SPEAKING) }
        playStartSpeakingSound()
        recorder.start()
        _uiState.update { it.copy(isRecording = true) }
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART2_SPEAKING)

        val transcribedText = stopRecordingAndTranscribe()
        if (transcribedText.isNotBlank()) {
            addTranscript("User", transcribedText)
        }

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
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART3_PREP)
        startPart3_Speaking()
    }

    private suspend fun startPart3_Speaking() {
        _uiState.update { it.copy(stage = MultilevelExamStage.PART3_SPEAKING) }
        playStartSpeakingSound()
        recorder.start()
        _uiState.update { it.copy(isRecording = true) }
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART3_SPEAKING)

        val transcribedText = stopRecordingAndTranscribe()
        if (transcribedText.isNotBlank()) {
            addTranscript("User", transcribedText)
        }

        concludeAndAnalyze()
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

    // --- ### THE CORE FIXES ARE HERE ### ---

    private suspend fun startAnswerTimer(prepTime: Int, answerTime: Int) {
        timerJob?.cancel()
        val newTimerJob = viewModelScope.launch {
            // Prep Phase
            _uiState.update { it.copy(isRecording = false) }
            for (i in prepTime downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0) }

            // Speaking Phase
            playStartSpeakingSound()
            recorder.start()
            _uiState.update { it.copy(isRecording = true) }
            for (i in answerTime downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0) }

            // Stop and Transcribe Phase (now waits)
            val transcribedText = stopRecordingAndTranscribe()
            if (transcribedText.isNotBlank()) {
                addTranscript("User", transcribedText)
            }
        }
        timerJob = newTimerJob
        newTimerJob.join() // This waits for the entire sequence to complete.
    }

    /**
     * Stops the recorder and suspends the current coroutine until transcription is complete.
     * @return The transcribed text.
     */
    private suspend fun stopRecordingAndTranscribe(): String {
        return suspendCancellableCoroutine { continuation ->
            // Store the continuation to be resumed by the callback.
            transcriptionContinuation = continuation
            recorder.stop()
            // The coroutine is now suspended. It will be resumed in onRecordingStopped().
        }
    }

    /**
     * This callback is invoked from the Recorder's background thread when it has fully stopped.
     */
    override fun onRecordingStopped() {
        // We are off the main thread here, but we need to run suspend functions.
        // Launch a new coroutine in the ViewModel's scope to handle it.
        viewModelScope.launch {
            _uiState.update { it.copy(isRecording = false) }

            val transcribedText = transcribeBufferedAudio()
            Log.d("ExamVM", "Recording stopped. Transcribed: '$transcribedText'")

            // Safely resume the suspended coroutine with the transcription result.
            transcriptionContinuation?.resume(transcribedText)
            transcriptionContinuation = null // Clean up to prevent leaks.
        }
    }

    // --- Helper and Listener functions ---

    private suspend fun startTimer(duration: Int) {
        timerJob?.cancel()
        val newTimerJob = viewModelScope.launch {
            for (i in duration downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0) }
        }
        timerJob = newTimerJob
        newTimerJob.join() // Wait here
    }

    override fun onDataReceived(samples: FloatArray) {
        synchronized(audioDataBuffer) {
            audioDataBuffer.add(samples)
        }
    }

    private suspend fun transcribeBufferedAudio(): String {
        val allSamples: FloatArray
        synchronized(audioDataBuffer) {
            if (audioDataBuffer.isEmpty()) {
                return ""
            }
            allSamples = FloatArray(audioDataBuffer.sumOf { it.size })
            var destinationPos = 0
            audioDataBuffer.forEach { chunk ->
                System.arraycopy(chunk, 0, allSamples, destinationPos, chunk.size)
                destinationPos += chunk.size
            }
            audioDataBuffer.clear()
        }

        return withContext(Dispatchers.Default) {
            try {
                whisperEngine?.transcribeBuffer(allSamples) ?: ""
            } catch (t: Throwable) {
                Log.e("ExamViewModel", "Whisper transcription failed", t)
                ""
            }
        }
    }

    private suspend fun playInstructionAndWait(@RawRes resId: Int) {
        try {
            AudioPlayer.playFromRawAndWait(context, resId)
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

    private fun getAssetFile(assetName: String): File {
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { it.copyTo(file.outputStream()) }
        }
        return file
    }

    override fun onCleared() {
        super.onCleared()
        recorder.stop()
        AudioPlayer.release()
        timerJob?.cancel()
        transcriptionContinuation?.resume("") // Resume with empty to avoid leaks if VM is cleared mid-transcription
        transcriptionContinuation = null
//        whisperEngine?.release()
    }
}