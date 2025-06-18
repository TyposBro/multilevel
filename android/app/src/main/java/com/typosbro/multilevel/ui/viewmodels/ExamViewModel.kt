package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.CueCard
import com.typosbro.multilevel.data.remote.models.ExamStepRequest
import com.typosbro.multilevel.data.remote.models.ExamStepResponse
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.remote.models.RepositoryResult
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

enum class ExamPart {
    NOT_STARTED,
    PART_1,
    PART_2_PREP,
    PART_2_SPEAKING,
    PART_3,
    FINISHED,
    ANALYSIS_COMPLETE
}

data class ExamUiState(
    val currentPart: ExamPart = ExamPart.NOT_STARTED,
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val isReadyForUserInput: Boolean = false,
    val examinerMessage: String? = null,
    val partialTranscription: String = "",
    val part2CueCard: CueCard? = null,
    val timerValue: Int = 0,
    val finalResultId: String? = null,
    val error: String? = null
)

@HiltViewModel
class ExamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository
) : ViewModel(), Recorder.RecorderListener {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState = _uiState.asStateFlow()

    private var whisperEngine: WhisperEngineNative? = null
    private var recorder: Recorder? = null

    private var part1TimerJob: Job? = null
    private var part2PrepTimerJob: Job? = null
    private var part2SpeakingTimerJob: Job? = null
    private var part3TimerJob: Job? = null
    private var isExamFinishedByTimer = AtomicBoolean(false)

    private val audioDataBuffer = mutableListOf<FloatArray>()
    private val transcript = mutableListOf<TranscriptEntry>()
    private var questionCountInPart = 0
    private var audioQueue = mutableListOf<List<Int>>()
    private var isPlayingAudio = false

    companion object {
        const val PART_1_DURATION = 20 // 5 minutes
        const val PART_2_PREP_DURATION = 10 // 1 minute
        const val PART_2_SPEAKING_DURATION = 20 // 2 minutes
        const val PART_3_DURATION = 20 // 5 minutes
        const val FINAL_ANSWER_WINDOW_S = 10 // Last 10 seconds of Part 3
    }

    private fun updateState(caller: String, transform: (ExamUiState) -> ExamUiState) {
        val currentState = _uiState.value
        val newState = transform(currentState)
        _uiState.value = newState
        if (currentState != newState) {
            Log.d("STATE_UPDATE", "Caller: $caller")
            Log.d("STATE_UPDATE", "  - OLD: $currentState")
            Log.d("STATE_UPDATE", "  - NEW: $newState")
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            updateState("init") { it.copy(isLoading = true) }
            whisperEngine = WhisperEngineNative(context).also {
                val modelFile = getAssetFile("whisper-tiny.en.tflite")
                it.initialize(modelFile.absolutePath, "", false)
            }
            recorder = Recorder(context, this@ExamViewModel)
            withContext(Dispatchers.Main) {
                updateState("init_complete") { it.copy(isLoading = false) }
            }
        }
    }

    fun onNavigationToResultConsumed() {
        updateState("onNavigationToResultConsumed") { it.copy(finalResultId = null) }
    }

    override fun onDataReceived(samples: FloatArray) {
        synchronized(audioDataBuffer) {
            audioDataBuffer.add(samples)
        }
    }

    override fun onRecordingStopped() {
        viewModelScope.launch {
            Log.d("ExamViewModel", "Recording stopped. Processing collected audio.")
            transcribeBufferedAudio()
        }
    }

    private suspend fun transcribeBufferedAudio() {
        val allSamples: FloatArray
        synchronized(audioDataBuffer) {
            if (audioDataBuffer.isEmpty()) {
                handleTranscriptionResult("")
                return
            }
            allSamples = FloatArray(audioDataBuffer.sumOf { it.size })
            var destinationPos = 0
            audioDataBuffer.forEach { chunk ->
                System.arraycopy(chunk, 0, allSamples, destinationPos, chunk.size)
                destinationPos += chunk.size
            }
            audioDataBuffer.clear()
        }

        val fullTranscription = withContext(Dispatchers.Default) {
            try {
                whisperEngine?.transcribeBuffer(allSamples) ?: ""
            } catch (t: Throwable) {
                Log.e("ExamViewModel", "Whisper transcription failed", t)
                ""
            }
        }

        Log.d("ExamViewModel", "Full transcription result: '$fullTranscription'")
        updateState("transcribeBufferedAudio") { it.copy(partialTranscription = fullTranscription) }

        if (fullTranscription.isNotBlank()) {
            transcript.add(TranscriptEntry("User", fullTranscription))
        }

        handleTranscriptionResult(fullTranscription)
    }

    private fun handleTranscriptionResult(transcription: String) {
        if (isExamFinishedByTimer.get()) {
            concludeExamAndAnalyze()
            return
        }
        when (_uiState.value.currentPart) {
            ExamPart.PART_1 -> continueWithPart1Questions(transcription)
            ExamPart.PART_2_SPEAKING -> moveToPart3()
            ExamPart.PART_3 -> continueWithPart3Questions(transcription)
            else -> { /* Do nothing */ }
        }
    }

    fun startExam() {
        if (_uiState.value.isLoading) return
        questionCountInPart = 0
        transcript.clear()
        isExamFinishedByTimer.set(false)
        cancelAllTimers()
        updateState("startExam") {
            it.copy(
                isLoading = true,
                currentPart = ExamPart.PART_1,
                examinerMessage = "Starting IELTS Speaking Test - Part 1..."
            )
        }
        startPart1Timer()
        viewModelScope.launch {
            when (val result = chatRepository.getInitialExamQuestion()) {
                is RepositoryResult.Success -> {
                    val response = result.data
                    transcript.add(TranscriptEntry("Examiner", response.examinerLine))
                    updateState("startExam_success") {
                        it.copy(
                            isLoading = false,
                            examinerMessage = response.examinerLine,
                            isReadyForUserInput = false
                        )
                    }
                    playAudioQueue(listOfNotNull(response.inputIds))
                }
                is RepositoryResult.Error -> updateState("startExam_error") { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    private fun startPart1Timer() {
        part1TimerJob = viewModelScope.launch {
            for (i in PART_1_DURATION downTo 1) {
                updateState("part1Timer") { it.copy(timerValue = i) }
                delay(1000)
            }
            if (_uiState.value.currentPart == ExamPart.PART_1) {
                Log.d("ExamViewModel", "Part 1 timer finished. Moving to Part 2.")
                if (_uiState.value.isRecording) recorder?.stop() else moveToPart2()
            }
        }
    }

    private fun continueWithPart1Questions(userInput: String) {
        updateState("continueWithPart1Questions") { it.copy(isLoading = true, partialTranscription = "") }
        questionCountInPart++
        val request = ExamStepRequest(
            part = 1,
            userInput = userInput,
            transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCountInPart
        )
        processExamStep(request) { response ->
            if (response.examinerLine.isNotBlank()) transcript.add(TranscriptEntry("Examiner", response.examinerLine))
            updateState("continueWithPart1Questions_success") { it.copy(isLoading = false, examinerMessage = response.examinerLine) }
            playAudioQueue(listOfNotNull(response.inputIds))
        }
    }

    private fun moveToPart2() {
        part1TimerJob?.cancel()
        questionCountInPart = 0
        updateState("moveToPart2") {
            it.copy(
                currentPart = ExamPart.PART_2_PREP,
                examinerMessage = "Moving to Part 2...",
                isLoading = true, timerValue = 0, isRecording = false,
                isReadyForUserInput = false, partialTranscription = ""
            )
        }
        viewModelScope.launch {
            val request = ExamStepRequest(
                part = 2,
                userInput = "Moving to Part 2. Provide the standard instructions for Part 2, but DO NOT mention the topic itself in your spoken line.",
                transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
                questionCountInPart = 0
            )
            processExamStep(request) { response ->
                if (response.examinerLine.isNotBlank()) transcript.add(TranscriptEntry("Examiner", response.examinerLine))
                updateState("moveToPart2_success") {
                    it.copy(
                        isLoading = false,
                        currentPart = ExamPart.PART_2_PREP,
                        part2CueCard = response.cueCard,
                        examinerMessage = response.examinerLine
                    )
                }
                playAudioQueue(listOfNotNull(response.inputIds)) { startPart2PrepTimer() }
            }
        }
    }

    private fun startPart2PrepTimer() {
        part2PrepTimerJob = viewModelScope.launch {
            for (i in PART_2_PREP_DURATION downTo 1) {
                updateState("part2PrepTimer") { it.copy(timerValue = i) }
                delay(1000)
            }
            updateState("startPart2PrepTimer_finished") {
                it.copy(
                    currentPart = ExamPart.PART_2_SPEAKING,
                    examinerMessage = "Your preparation time is over. Please start speaking now. You have up to 2 minutes.",
                    timerValue = 0
                )
            }
            startPart2SpeakingTimer()
            startUserSpeechRecognition()
        }
    }

    private fun startPart2SpeakingTimer() {
        part2SpeakingTimerJob = viewModelScope.launch {
            for (i in PART_2_SPEAKING_DURATION downTo 1) {
                updateState("part2SpeakingTimer") { it.copy(timerValue = i) }
                delay(1000)
            }
            if (_uiState.value.currentPart == ExamPart.PART_2_SPEAKING) {
                if (_uiState.value.isRecording) recorder?.stop() else moveToPart3()
            }
        }
    }

    private fun moveToPart3() {
        part2SpeakingTimerJob?.cancel()
        part2PrepTimerJob?.cancel()
        questionCountInPart = 0
        updateState("moveToPart3") {
            it.copy(
                currentPart = ExamPart.PART_3,
                examinerMessage = "Thank you.",
                isLoading = true, timerValue = 0, isRecording = false,
                isReadyForUserInput = false, partialTranscription = "", part2CueCard = null
            )
        }
        startPart3Timer()
        viewModelScope.launch {
            val request = ExamStepRequest(
                part = 3,
                userInput = "The user has just finished their Part 2 talk. Say 'Thank you' and then begin Part 3 by asking the first discussion question.",
                transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
                questionCountInPart = 0
            )
            processExamStep(request) { response ->
                if (response.examinerLine.isNotBlank()) transcript.add(TranscriptEntry("Examiner", response.examinerLine))
                updateState("moveToPart3_success") { it.copy(isLoading = false, examinerMessage = response.examinerLine) }
                playAudioQueue(listOfNotNull(response.inputIds))
            }
        }
    }

    private fun startPart3Timer() {
        part3TimerJob = viewModelScope.launch {
            for (i in PART_3_DURATION downTo 1) {
                updateState("part3Timer") { it.copy(timerValue = i) }
                delay(1000)
            }
            if (_uiState.value.currentPart == ExamPart.PART_3 && !isExamFinishedByTimer.get()) {
                Log.d("ExamViewModel", "Part 3 timer finished. Stopping recording to finalize.")
                isExamFinishedByTimer.set(true)
                if (_uiState.value.isRecording) {
                    recorder?.stop()
                } else {
                    concludeExamAndAnalyze()
                }
            }
        }
    }

    private fun continueWithPart3Questions(userInput: String) {
        if (_uiState.value.currentPart >= ExamPart.FINISHED) return
        updateState("continueWithPart3Questions") { it.copy(isLoading = true, partialTranscription = "") }
        if (_uiState.value.timerValue <= FINAL_ANSWER_WINDOW_S) {
            Log.d("ExamViewModel", "Final answer received in time window. Concluding exam.")
            isExamFinishedByTimer.set(true)
            concludeExamAndAnalyze()
            return
        }
        questionCountInPart++
        val request = ExamStepRequest(
            part = 3,
            userInput = userInput,
            transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCountInPart
        )
        processExamStep(request) { response ->
            if (response.examinerLine.isNotBlank()) transcript.add(TranscriptEntry("Examiner", response.examinerLine))
            updateState("continueWithPart3Questions_success") { it.copy(isLoading = false, examinerMessage = response.examinerLine) }
            playAudioQueue(listOfNotNull(response.inputIds))
        }
    }

    private fun concludeExamAndAnalyze() {
        if (_uiState.value.currentPart >= ExamPart.FINISHED) return
        cancelAllTimers()
        val finalMessage = "Thank you, that's the end of the IELTS Speaking Test. You will receive your results in a few moments."
        transcript.add(TranscriptEntry("Examiner", finalMessage))
        updateState("concludeExamAndAnalyze") {
            it.copy(
                currentPart = ExamPart.FINISHED,
                examinerMessage = finalMessage,
                timerValue = 0,
                isRecording = false,
                isReadyForUserInput = false,
                isLoading = true
            )
        }
        analyzeExam()
    }

    private fun analyzeExam() {
        viewModelScope.launch {
            when (val result = chatRepository.analyzeFullExam(transcript)) {
                is RepositoryResult.Success -> {
                    updateState("analyzeExam_success") {
                        it.copy(
                            currentPart = ExamPart.ANALYSIS_COMPLETE,
                            isLoading = false,
                            finalResultId = result.data.resultId
                        )
                    }
                }
                is RepositoryResult.Error -> {
                    Log.e("ExamViewModel", "Analysis failed: ${result.message}")
                    updateState("analyzeExam_error") {
                        it.copy(isLoading = false, error = "Analysis failed. Please try again later.")
                    }
                }
            }
        }
    }

    private fun processExamStep(request: ExamStepRequest, onSuccess: (response: ExamStepResponse) -> Unit) {
        if (_uiState.value.currentPart >= ExamPart.FINISHED) return
        viewModelScope.launch {
            when (val result = chatRepository.getNextExamStep(request)) {
                is RepositoryResult.Success -> { onSuccess(result.data) }
                is RepositoryResult.Error -> { updateState("processExamStep_error") { it.copy(isLoading = false, error = result.message) } }
            }
        }
    }

    fun startUserSpeechRecognition() {
        if (recorder?.isRecording == true || isPlayingAudio || _uiState.value.currentPart >= ExamPart.FINISHED) return
        audioDataBuffer.clear()
        updateState("startUserSpeechRecognition") { it.copy(isRecording = true, partialTranscription = "", isReadyForUserInput = true) }
        recorder?.start()
    }

    fun stopUserSpeechRecognition() {
        if (recorder?.isRecording != true) return
        if (_uiState.value.currentPart == ExamPart.PART_2_SPEAKING) {
            part2SpeakingTimerJob?.cancel()
        }
        recorder?.stop()
        updateState("stopUserSpeechRecognition") { it.copy(isRecording = false, isReadyForUserInput = false, partialTranscription = "Processing...") }
    }

    private fun playAudioQueue(ids: List<List<Int>>, onComplete: (() -> Unit)? = null) {
        if (_uiState.value.currentPart >= ExamPart.FINISHED || ids.isEmpty() || ids.firstOrNull()?.isEmpty() == true) {
            if (onComplete != null) {
                onComplete()
            } else if (_uiState.value.currentPart < ExamPart.FINISHED) {
                startUserSpeechRecognition()
            }
            return
        }
        isPlayingAudio = true
        updateState("playAudioQueue_start") { it.copy(isReadyForUserInput = false) }
        audioQueue.clear()
        audioQueue.addAll(ids)
        playNextAudioInQueue(onComplete)
    }

    private fun playNextAudioInQueue(onComplete: (() -> Unit)? = null) {
        if (audioQueue.isEmpty()) {
            isPlayingAudio = false
            if (onComplete != null) {
                onComplete()
            } else if (_uiState.value.currentPart < ExamPart.FINISHED) {
                startUserSpeechRecognition()
            }
            return
        }
        viewModelScope.launch {
            val audioBytes = AudioPlayer.createAudioAndConvertToWav(audioQueue.removeAt(0).map { it.toLong() }, context)
            AudioPlayer.playAudio(context, audioBytes) { playNextAudioInQueue(onComplete) }
        }
    }

    private fun cancelAllTimers() {
        part1TimerJob?.cancel()
        part2PrepTimerJob?.cancel()
        part2SpeakingTimerJob?.cancel()
        part3TimerJob?.cancel()
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
        Log.d("ExamViewModel", "onCleared: Releasing all resources.")
        recorder?.stop()
        whisperEngine?.deinitialize()
        AudioPlayer.release()
        cancelAllTimers()
    }
}