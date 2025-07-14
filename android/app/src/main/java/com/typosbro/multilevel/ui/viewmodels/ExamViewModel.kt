package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.annotation.RawRes
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    // ADDED: Property to hold the current audio file being recorded
    private var currentAudioFile: File? = null

    // ADDED: Helper function for logged state updates
    private fun updateState(caller: String, transform: (MultilevelUiState) -> MultilevelUiState) {
        Log.i("STATE_UPDATE", "Request from [$caller]")
        _uiState.update(transform)
    }

    init {
        // ADDED: Collector to log the full state on every change
        _uiState.onEach { newState ->
            Log.d("STATE_DEBUG", "New State -> $newState")
        }.launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            whisperEngine = WhisperEngineNative(context).also {
                val modelFile = getAssetFile("whisper-tiny.en.tflite")
                it.initialize(modelFile.absolutePath, "", false)
            }
        }
    }

    fun startExam() {
        if (uiState.value.stage != MultilevelExamStage.NOT_STARTED) return
        updateState("startExam: Loading") { it.copy(stage = MultilevelExamStage.LOADING) }

        viewModelScope.launch {
            when (val result = repository.getNewExam()) {
                is RepositoryResult.Success -> {
                    updateState("startExam: Success") { it.copy(examContent = result.data) }
                    when (practicePart) {
                        PracticePart.FULL, PracticePart.P1_1 -> {
                            updateState("startExam: Start P1_1") { it.copy(stage = MultilevelExamStage.INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part1_intro)
                            startPart1_1()
                        }

                        PracticePart.P1_2 -> {
                            updateState("startExam: Start P1_2") { it.copy(stage = MultilevelExamStage.PART1_2_INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part1_2_intro)
                            processPart1_2_Question()
                        }

                        PracticePart.P2 -> {
                            updateState("startExam: Start P2") { it.copy(stage = MultilevelExamStage.PART2_INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part2_intro)
                            startPart2_Prep()
                        }

                        PracticePart.P3 -> {
                            updateState("startExam: Start P3") { it.copy(stage = MultilevelExamStage.PART3_INTRO) }
                            playInstructionAndWait(R.raw.multilevel_part3_intro)
                            startPart3_Prep()
                        }
                    }
                }

                is RepositoryResult.Error -> {
                    updateState("startExam: Error") {
                        it.copy(stage = MultilevelExamStage.FINISHED_ERROR, error = result.message)
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
                if (practicePart == PracticePart.FULL) startPart1_2() else concludeAndAnalyze()
                return@launch
            }

            val question = content[index]
            updateState("startPart1_1: Question ${index + 1}") {
                it.copy(
                    stage = MultilevelExamStage.PART1_1_QUESTION,
                    currentQuestionText = question.questionText
                )
            }
            addTranscript(TranscriptEntry("Examiner", question.questionText))
            AudioPlayer.playFromUrlAndWait(context, question.audioUrl)

            startAnswerTimer(MULTILEVEL_TIMEOUTS.PART1_1_PREP, MULTILEVEL_TIMEOUTS.PART1_1_ANSWER)

            updateState("startPart1_1: Incrementing index") { it.copy(part1_1_QuestionIndex = index + 1) }
            startPart1_1() // Recursive call for next question
        }
    }

    private fun startPart1_2() {
        viewModelScope.launch {
            updateState("startPart1_2: Intro") { it.copy(stage = MultilevelExamStage.PART1_2_INTRO) }
            playInstructionAndWait(R.raw.multilevel_part1_2_intro)
            processPart1_2_Question()
        }
    }

    private fun processPart1_2_Question() {
        viewModelScope.launch {
            val set = _uiState.value.examContent?.part1_2 ?: return@launch
            val index = _uiState.value.part1_2_QuestionIndex
            if (index >= set.questions.size) {
                if (practicePart == PracticePart.FULL) startPart2() else concludeAndAnalyze()
                return@launch
            }

            val question = set.questions[index]
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

            updateState("processPart1_2_Question: Question ${index + 1}") {
                it.copy(
                    stage = currentStage,
                    currentQuestionText = question.text
                )
            }
            addTranscript(TranscriptEntry("Examiner", question.text))
            AudioPlayer.playFromUrlAndWait(context, question.audioUrl)

            startAnswerTimer(prepTime, answerTime)

            updateState("processPart1_2_Question: Incrementing index") {
                it.copy(
                    part1_2_QuestionIndex = index + 1
                )
            }
            processPart1_2_Question()
        }
    }

    private fun startPart2() {
        viewModelScope.launch {
            updateState("startPart2: Intro") { it.copy(stage = MultilevelExamStage.PART2_INTRO) }
            playInstructionAndWait(R.raw.multilevel_part2_intro)
            startPart2_Prep()
        }
    }

    private suspend fun startPart2_Prep() {
        val set = _uiState.value.examContent?.part2 ?: return
        val fullQuestionText = set.questions.joinToString("\n") { it.text }
        updateState("startPart2_Prep") {
            it.copy(
                stage = MultilevelExamStage.PART2_PREP,
                currentQuestionText = fullQuestionText
            )
        }
        addTranscript(TranscriptEntry("Examiner", fullQuestionText))
        AudioPlayer.playFromUrlAndWait(context, set.questions.firstOrNull()?.audioUrl ?: "")
        startTimer(MULTILEVEL_TIMEOUTS.PART2_PREP)
        startPart2_Speaking()
    }

    private suspend fun startPart2_Speaking() {
        updateState("startPart2_Speaking") { it.copy(stage = MultilevelExamStage.PART2_SPEAKING) }
        playStartSpeakingSound()

        // MODIFIED: Start recording and store the file
        currentAudioFile = recorder.start()
        updateState("startPart2_Speaking: Recording started") { it.copy(isRecording = true) }
        startTimer(MULTILEVEL_TIMEOUTS.PART2_SPEAKING)

        val transcribedText = stopRecordingAndTranscribe()
        if (transcribedText.isNotBlank()) {
            addTranscript(TranscriptEntry("User", transcribedText))
        }

        if (practicePart == PracticePart.FULL) startPart3() else concludeAndAnalyze()
    }

    private fun startPart3() {
        viewModelScope.launch {
            updateState("startPart3: Intro") { it.copy(stage = MultilevelExamStage.PART3_INTRO) }
            playInstructionAndWait(R.raw.multilevel_part3_intro)
            startPart3_Prep()
        }
    }

    private suspend fun startPart3_Prep() {
        updateState("startPart3_Prep") { it.copy(stage = MultilevelExamStage.PART3_PREP) }
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART3_PREP)
        startPart3_Speaking()
    }

    private suspend fun startPart3_Speaking() {
        updateState("startPart3_Speaking") { it.copy(stage = MultilevelExamStage.PART3_SPEAKING) }
        playStartSpeakingSound()

        // MODIFIED: Start recording and store the file
        currentAudioFile = recorder.start()
        updateState("startPart3_Speaking: Recording started") { it.copy(isRecording = true) }
        startTimer(duration = MULTILEVEL_TIMEOUTS.PART3_SPEAKING)

        val transcribedText = stopRecordingAndTranscribe()
        if (transcribedText.isNotBlank()) {
            addTranscript(TranscriptEntry("User", transcribedText))
        }

        concludeAndAnalyze()
    }

    private fun concludeAndAnalyze() {
        updateState("concludeAndAnalyze: Analyzing") { it.copy(stage = MultilevelExamStage.ANALYZING) }

        // ADDED: Save the final, complete transcript log
        val logDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        if (logDir != null) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            // Use a .wav extension so the repository can correctly parse the name
            val finalLogFile = File(logDir, "full_exam_transcript_$timestamp.wav")
            DebugLogRepository.saveTranscript(context, finalLogFile, uiState.value.transcript)
        }

        viewModelScope.launch {
            val examContent = uiState.value.examContent ?: return@launch
            val partString = if (practicePart == PracticePart.FULL) null else practicePart.name
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

    private suspend fun startAnswerTimer(prepTime: Int, answerTime: Int) {
        timerJob?.cancel()
        val newTimerJob = viewModelScope.launch {
            updateState("startAnswerTimer: Prep phase") { it.copy(isRecording = false) }
            for (i in prepTime downTo 1) {
                updateState("startAnswerTimer: Prep countdown") { it.copy(timerValue = i) }
                delay(1000)
            }
            updateState("startAnswerTimer: Prep finished") { it.copy(timerValue = 0) }

            playStartSpeakingSound()
            // MODIFIED: Start recording and store the file
            currentAudioFile = recorder.start()
            updateState("startAnswerTimer: Answer phase") { it.copy(isRecording = true) }
            for (i in answerTime downTo 1) {
                updateState("startAnswerTimer: Answer countdown") { it.copy(timerValue = i) }
                delay(1000)
            }
            updateState("startAnswerTimer: Answer finished") { it.copy(timerValue = 0) }

            val transcribedText = stopRecordingAndTranscribe()
            if (transcribedText.isNotBlank()) {
                addTranscript(TranscriptEntry("User", transcribedText))
            }
        }
        timerJob = newTimerJob
        newTimerJob.join()
    }

    private suspend fun stopRecordingAndTranscribe(): String {
        return suspendCancellableCoroutine { continuation ->
            transcriptionContinuation = continuation
            recorder.stop()
        }
    }

    override fun onRecordingStopped() {
        viewModelScope.launch {
            try {
                updateState("onRecordingStopped") { it.copy(isRecording = false) }
                playStartSpeakingSound() // Play sound to indicate recording has stopped
                val transcribedText = transcribeBufferedAudio()
                val cleanText =
                    if (transcribedText.isBlank() || transcribedText.all { !it.isLetter() }) "" else transcribedText
                Log.d("ExamVM", "Recording stopped. Transcribed: '$cleanText'")

                // ADDED: Save the partial transcript for this specific recording session
                if (cleanText.isNotBlank()) {
                    DebugLogRepository.saveTranscript(
                        context,
                        currentAudioFile,
                        listOf(TranscriptEntry("User", cleanText))
                    )
                }
                transcriptionContinuation?.resume(cleanText)
            } catch (e: Exception) {
                Log.e("ExamVM", "Error in onRecordingStopped", e)
                transcriptionContinuation?.resume("")
            } finally {
                transcriptionContinuation = null
                currentAudioFile = null // ADDED: Clear the file reference for the next recording
            }
        }
    }

    private suspend fun startTimer(duration: Int) {
        timerJob?.cancel()
        val newTimerJob = viewModelScope.launch {
            for (i in duration downTo 1) {
                updateState("startTimer: countdown") { it.copy(timerValue = i) }
                delay(1000)
            }
            updateState("startTimer: finished") { it.copy(timerValue = 0) }
        }
        timerJob = newTimerJob
        newTimerJob.join()
    }

    override fun onDataReceived(samples: FloatArray) {
        synchronized(audioDataBuffer) {
            audioDataBuffer.add(samples)
        }
    }

    private suspend fun transcribeBufferedAudio(): String {
        val allSamples: FloatArray
        synchronized(audioDataBuffer) {
            if (audioDataBuffer.isEmpty()) return ""
            val totalSamples = audioDataBuffer.sumOf { it.size }
            if (totalSamples < 1600) { // Don't process very short/empty clips
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

    // MODIFIED: Now accepts a TranscriptEntry object for consistency
    private fun addTranscript(newEntry: TranscriptEntry) {
        updateState("addTranscript") { it.copy(transcript = it.transcript + newEntry) }
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
        if (currentStage in listOf(
                MultilevelExamStage.NOT_STARTED,
                MultilevelExamStage.LOADING,
                MultilevelExamStage.FINISHED_ERROR,
                MultilevelExamStage.ANALYZING
            )
        ) {
            return
        }
        Log.w("ExamVM", "Stopping exam forcefully due to interruption.")
        timerJob?.cancel()
        recorder.stop()
        AudioPlayer.release()
        transcriptionContinuation?.resume("")
        transcriptionContinuation = null
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
        whisperEngine?.deinitialize()
    }
}