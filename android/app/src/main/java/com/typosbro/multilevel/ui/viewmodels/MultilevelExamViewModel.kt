package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.BuildConfig
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

// Enum to define the practice parts for type safety
enum class PracticePart {
    FULL, P1_1, P1_2, P2, P3
}

enum class MultilevelExamStage {
    NOT_STARTED, LOADING, INTRO, PART1_1_QUESTION, PART1_2_INTRO, PART1_2_COMPARE,
    PART1_2_FOLLOWUP, PART2_INTRO, PART2_PREP, PART2_SPEAKING, PART3_INTRO,
    PART3_PREP, PART3_SPEAKING, ANALYZING, FINISHED_ERROR
}

//
//object MULTILEVEL_TIMEOUTS {
//    const val PART1_1_PREP = 5
//    const val PART1_1_ANSWER = 30
//    const val PART1_2_PREP_FIRST = 10
//    const val PART1_2_PREP_FOLLOWUP = 5
//    const val PART1_2_ANSWER_FIRST = 45
//    const val PART1_2_ANSWER_FOLLOWUP = 30
//    const val PART2_PREP = 60
//    const val PART2_SPEAKING = 120
//    const val PART3_PREP = 60
//    const val PART3_SPEAKING = 120
//}

object MULTILEVEL_TIMEOUTS {
    const val PART1_1_PREP = 3
    const val PART1_1_ANSWER = 3
    const val PART1_2_PREP_FIRST = 3
    const val PART1_2_PREP_FOLLOWUP = 3
    const val PART1_2_ANSWER_FIRST = 3
    const val PART1_2_ANSWER_FOLLOWUP = 3
    const val PART2_PREP = 3
    const val PART2_SPEAKING = 3
    const val PART3_PREP = 3
    const val PART3_SPEAKING = 3
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
    savedStateHandle: SavedStateHandle // Inject SavedStateHandle
) : ViewModel(), Recorder.RecorderListener {

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

    private var whisperEngine: WhisperEngineNative? = null
    private val recorder: Recorder = Recorder(context, this)
    private var timerJob: Job? = null
    private val audioDataBuffer = mutableListOf<FloatArray>()
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
                    _uiState.update { it.copy(examContent = result.data) }
                    // Jump to the correct starting point based on the practice part
                    when (practicePart) {
                        PracticePart.FULL, PracticePart.P1_1 -> {
                            _uiState.update { it.copy(stage = MultilevelExamStage.INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part1_intro)
                            startPart1_1()
                        }

                        PracticePart.P1_2 -> {
                            _uiState.update { it.copy(stage = MultilevelExamStage.PART1_2_INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part1_2_intro)
                            processPart1_2_Question()
                        }

                        PracticePart.P2 -> {
                            _uiState.update { it.copy(stage = MultilevelExamStage.PART2_INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part2_intro)
                            startPart2_Prep()
                        }

                        PracticePart.P3 -> {
                            _uiState.update { it.copy(stage = MultilevelExamStage.PART3_INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part3_intro)
                            startPart3_Prep()
                        }
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
        viewModelScope.launch {
            val content = _uiState.value.examContent?.part1_1 ?: return@launch
            val index = _uiState.value.part1_1_QuestionIndex
            if (index >= content.size) {
                // If this part is done, either move to the next or finish.
                if (practicePart == PracticePart.FULL) {
                    startPart1_2()
                } else {
                    concludeAndAnalyze()
                }
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
                if (practicePart == PracticePart.FULL) {
                    startPart2()
                } else {
                    concludeAndAnalyze()
                }
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
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART2_SPEAKING)

        stopRecordingAndTranscribe() // This handles recording internally now

        if (practicePart == PracticePart.FULL) {
            startPart3()
        } else {
            concludeAndAnalyze()
        }
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
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART3_SPEAKING)

        stopRecordingAndTranscribe() // This handles recording internally now

        concludeAndAnalyze()
    }

    // In MultilevelExamViewModel.kt

    private fun concludeAndAnalyze() {
        _uiState.update { it.copy(stage = MultilevelExamStage.ANALYZING) }
        viewModelScope.launch {
            // Get a stable reference to the state at the start of the coroutine.
            val currentState = uiState.value
            val examContent = currentState.examContent
            val transcript = currentState.transcript

            // Defensive check: If for some reason examContent is null, we can't proceed.
            if (examContent == null) {
                _uiState.update {
                    it.copy(
                        stage = MultilevelExamStage.FINISHED_ERROR,
                        error = "Cannot analyze exam: Exam content was not loaded."
                    )
                }
                return@launch
            }

            // Conditionally build the contentIds object based on which part was practiced.
            val contentIds = when (practicePart) {
                PracticePart.FULL -> ExamContentIds(
                    part1_1 = examContent.part1_1.map { it.id },
                    part1_2 = examContent.part1_2.id,
                    part2 = examContent.part2.id,
                    part3 = examContent.part3.id
                )

                PracticePart.P1_1 -> ExamContentIds(
                    part1_1 = examContent.part1_1.map { it.id },
                    part1_2 = null,
                    part2 = null,
                    part3 = null
                )

                PracticePart.P1_2 -> ExamContentIds(
                    part1_1 = null,
                    part1_2 = examContent.part1_2.id,
                    part2 = null,
                    part3 = null
                )

                PracticePart.P2 -> ExamContentIds(
                    part1_1 = null,
                    part1_2 = null,
                    part2 = examContent.part2.id,
                    part3 = null
                )

                PracticePart.P3 -> ExamContentIds(
                    part1_1 = null,
                    part1_2 = null,
                    part2 = null,
                    // --- THIS IS THE KEY FIX ---
                    // Ensure that `examContent.part3` is not null before accessing `.id`.
                    // The server-side logic handles this, but client-side is good practice.
                    part3 = examContent.part3?.id
                )
            }

            val partString = if (practicePart == PracticePart.FULL) null else practicePart.name

            // Log the request payload before sending
            Log.d(
                "ExamVM_Analysis",
                "Sending analysis request: practicePart=$partString, contentIds=$contentIds, transcript size=${transcript.size}"
            )

            val request = MultilevelAnalyzeRequest(transcript, contentIds, partString)

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

    override fun onDataReceived(samples: FloatArray) {
        synchronized(audioDataBuffer) {
            audioDataBuffer.add(samples)
        }
    }

    override fun onRecordingStopped() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRecording = false) }
                playStartSpeakingSound()
                var transcribedText = transcribeBufferedAudio()

                // Inject dummy text in DEBUG mode if user didn't speak
                if (BuildConfig.DEBUG && transcribedText.isBlank()) {
                    Log.w("ExamVM_Debug", "Empty transcript in DEBUG mode. Injecting dummy text.")
                    val dummyText =
                        "This is a dummy transcript for testing purposes to allow the flow to continue without speaking."
                    addTranscript("User", dummyText)
                    transcribedText = dummyText
                } else if (transcribedText.isNotBlank()) {
                    addTranscript("User", transcribedText)
                }

                transcriptionContinuation?.resume(transcribedText)
            } catch (e: Exception) {
                Log.e("ExamVM", "Error in onRecordingStopped", e)
                transcriptionContinuation?.resume("")
            } finally {
                transcriptionContinuation = null
            }
        }
    }

    private suspend fun stopRecordingAndTranscribe(): String {
        return suspendCancellableCoroutine { continuation ->
            transcriptionContinuation = continuation
            recorder.stop()
        }
    }

    private suspend fun startAnswerTimer(prepTime: Int, answerTime: Int) {
        timerJob?.cancel()
        val newTimerJob = viewModelScope.launch {
            _uiState.update { it.copy(isRecording = false) }
            startTimer(duration = prepTime)

            playStartSpeakingSound()
            startTimer(duration = answerTime)

            stopRecordingAndTranscribe()
        }
        timerJob = newTimerJob
        newTimerJob.join()
    }

    private suspend fun startTimer(duration: Int) {
        timerJob?.cancel()
        val isSpeakingStage = uiState.value.stage == MultilevelExamStage.PART1_1_QUESTION ||
                uiState.value.stage == MultilevelExamStage.PART1_2_COMPARE ||
                uiState.value.stage == MultilevelExamStage.PART1_2_FOLLOWUP ||
                uiState.value.stage == MultilevelExamStage.PART2_SPEAKING ||
                uiState.value.stage == MultilevelExamStage.PART3_SPEAKING

        if (isSpeakingStage && !recorder.isRecording) {
            recorder.start()
            _uiState.update { it.copy(isRecording = true) }
        }

        val newTimerJob = viewModelScope.launch {
            for (i in duration downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update { it.copy(timerValue = 0) }
        }
        timerJob = newTimerJob
        newTimerJob.join()
    }

    private suspend fun transcribeBufferedAudio(): String {
        val allSamples: FloatArray
        synchronized(audioDataBuffer) {
            if (audioDataBuffer.isEmpty()) return ""
            val totalSamples = audioDataBuffer.sumOf { it.size }
            if (totalSamples < 1600) {
                audioDataBuffer.clear()
                return ""
            }

            allSamples = FloatArray(totalSamples)
            var destinationPos = 0
            audioDataBuffer.forEach { chunk ->
                System.arraycopy(chunk, 0, allSamples, destinationPos, chunk.size)
                destinationPos += chunk.size
            }
            audioDataBuffer.clear()

            val maxAmplitude = allSamples.maxOrNull() ?: 0f
            if (maxAmplitude < 0.001f) return ""
        }

        return withContext(Dispatchers.Default) {
            try {
                val rawResult = whisperEngine?.transcribeBuffer(allSamples) ?: ""
                sanitizeUtf8String(rawResult)
            } catch (t: Throwable) {
                Log.e("ExamViewModel", "Whisper transcription failed", t)
                ""
            }
        }
    }

    private fun sanitizeUtf8String(input: String): String {
        return try {
            input.replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")
                .replace(Regex("[\uFFFD\u0000-\u001F\u007F-\u009F]"), "")
                .trim()
        } catch (e: Exception) {
            Log.w("ExamViewModel", "Failed to sanitize UTF-8 string", e)
            ""
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

    fun stopExam() {
        val currentStage = uiState.value.stage
        if (currentStage == MultilevelExamStage.NOT_STARTED || currentStage == MultilevelExamStage.LOADING || currentStage == MultilevelExamStage.FINISHED_ERROR || currentStage == MultilevelExamStage.ANALYZING) {
            return
        }
        Log.w("ExamVM", "Stopping exam forcefully due to interruption.")
        timerJob?.cancel()
        recorder.stop()
        AudioPlayer.release()
        transcriptionContinuation?.resume("")
        transcriptionContinuation = null
        _uiState.update {
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
        whisperEngine?.deinitialize()
    }
}