// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ExamViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.CueCard
import com.typosbro.multilevel.data.remote.models.ExamEvent
import com.typosbro.multilevel.data.remote.models.ExamStreamEndData
import com.typosbro.multilevel.data.remote.models.ExamStepRequest
import com.typosbro.multilevel.data.remote.models.TranscriptEntry
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result
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
        const val PART_1_DURATION = 300 // 5 minutes
        const val PART_2_PREP_DURATION = 60 // 1 minute
        const val PART_2_SPEAKING_DURATION = 120 // 2 minutes
        const val PART_3_DURATION = 300 // 5 minutes
        const val FINAL_ANSWER_WINDOW_S = 10 // Last 10 seconds of Part 3
        const val REDIRECT_DELAY_MS = 2000L
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            whisperEngine = WhisperEngineNative(context).also {
                val modelFile = getAssetFile("whisper-tiny.en.tflite")
                it.initialize(modelFile.absolutePath, "", false)
            }
            recorder = Recorder(context, this@ExamViewModel)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onNavigationToResultConsumed() {
        _uiState.update { it.copy(finalResultId = null) }
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
            whisperEngine?.transcribeBuffer(allSamples) ?: ""
        }

        Log.d("ExamViewModel", "Full transcription result: '$fullTranscription'")
        _uiState.update { it.copy(partialTranscription = fullTranscription) }

        if (fullTranscription.isNotBlank()) {
            transcript.add(TranscriptEntry("User", fullTranscription))
        }

        handleTranscriptionResult(fullTranscription)
    }

    /**
     * [THE FIX - PART 1] This function is now the single source of truth for advancing the exam state
     * after the user speaks. It correctly handles all state transitions, including the final one.
     */
    private fun handleTranscriptionResult(transcription: String) {
        // If the Part 3 timer has run out, this transcription is the final one. Conclude the exam.
        if (isExamFinishedByTimer.get()) {
            concludeExamAndAnalyze()
            return
        }

        when (_uiState.value.currentPart) {
            ExamPart.PART_1 -> continueWithPart1Questions(transcription)
            ExamPart.PART_2_SPEAKING -> moveToPart3() // User finished long turn, now move to Part 3
            ExamPart.PART_3 -> continueWithPart3Questions(transcription)
            else -> { /* Do nothing for other states like NOT_STARTED, PART_2_PREP, etc. */ }
        }
    }

    // =================== Exam Flow and State Management ===================

    fun startExam() {
        if (_uiState.value.isLoading) return
        // Reset all state for a new exam
        questionCountInPart = 0
        transcript.clear()
        isExamFinishedByTimer.set(false)
        cancelAllTimers()

        _uiState.update {
            it.copy(
                isLoading = true,
                currentPart = ExamPart.PART_1,
                examinerMessage = "Starting IELTS Speaking Test - Part 1..."
            )
        }

        startPart1Timer()

        viewModelScope.launch {
            when (val result = chatRepository.getInitialExamQuestion()) {
                is Result.Success -> {
                    val response = result.data
                    transcript.add(TranscriptEntry("Examiner", response.examinerLine))
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            examinerMessage = response.examinerLine,
                            isReadyForUserInput = false
                        )
                    }
                    playAudioQueue(listOfNotNull(response.inputIds))
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    private fun startPart1Timer() {
        part1TimerJob = viewModelScope.launch {
            for (i in PART_1_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            if (_uiState.value.currentPart == ExamPart.PART_1) {
                Log.d("ExamViewModel", "Part 1 timer finished. Moving to Part 2.")
                if (_uiState.value.isRecording) recorder?.stop() else moveToPart2()
            }
        }
    }

    private fun continueWithPart1Questions(userInput: String) {
        _uiState.update { it.copy(isLoading = true, partialTranscription = "") }
        questionCountInPart++

        val request = ExamStepRequest(
            part = 1,
            userInput = userInput,
            transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCountInPart
        )

        processExamStepStream(request) { _, fullText, ttsChunks ->
            if (fullText.isNotBlank()) transcript.add(TranscriptEntry("Examiner", fullText))
            _uiState.update { it.copy(isLoading = false) }
            playAudioQueue(ttsChunks)
        }
    }

    private fun moveToPart2() {
        part1TimerJob?.cancel()
        questionCountInPart = 0

        _uiState.update {
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

            processExamStepStream(request) { endData, fullText, ttsChunks ->
                if (fullText.isNotBlank()) transcript.add(TranscriptEntry("Examiner", fullText))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPart = ExamPart.PART_2_PREP,
                        part2CueCard = endData.cue_card,
                        examinerMessage = fullText
                    )
                }
                playAudioQueue(ttsChunks) { startPart2PrepTimer() }
            }
        }
    }

    private fun startPart2PrepTimer() {
        part2PrepTimerJob = viewModelScope.launch {
            for (i in PART_2_PREP_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            _uiState.update {
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
                _uiState.update { it.copy(timerValue = i) }
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

        _uiState.update {
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

            processExamStepStream(request) { _, fullText, ttsChunks ->
                if (fullText.isNotBlank()) transcript.add(TranscriptEntry("Examiner", fullText))
                _uiState.update { it.copy(isLoading = false, examinerMessage = fullText) }
                playAudioQueue(ttsChunks)
            }
        }
    }

    /**
     * [THE FIX - PART 2] The Part 3 timer is simplified. On timeout, it just sets a flag and stops
     * the recorder. The actual exam conclusion is handled by `handleTranscriptionResult`.
     */
    private fun startPart3Timer() {
        part3TimerJob = viewModelScope.launch {
            for (i in PART_3_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
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

    /**
     * [THE FIX - PART 3] Logic is simplified. If the final time window is reached, it concludes the exam.
     * Otherwise, it proceeds with the next question.
     */
    private fun continueWithPart3Questions(userInput: String) {
        if (_uiState.value.currentPart >= ExamPart.FINISHED) return

        _uiState.update { it.copy(isLoading = true, partialTranscription = "") }

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
        processExamStepStream(request) { _, fullText, ttsChunks ->
            if (fullText.isNotBlank()) transcript.add(TranscriptEntry("Examiner", fullText))
            _uiState.update { it.copy(isLoading = false) }
            playAudioQueue(ttsChunks)
        }
    }

    /**
     * [THE FIX - PART 4] This new function is the single, unified way to end the exam.
     * It avoids all previous race conditions and faulty logic.
     */
    private fun concludeExamAndAnalyze() {
        if (_uiState.value.currentPart >= ExamPart.FINISHED) return // Prevent multiple calls

        cancelAllTimers()
        val finalMessage = "Thank you, that's the end of the IELTS Speaking Test. You will receive your results in a few moments."
        transcript.add(TranscriptEntry("Examiner", finalMessage))

        _uiState.update {
            it.copy(
                currentPart = ExamPart.FINISHED,
                examinerMessage = finalMessage,
                timerValue = 0,
                isRecording = false,
                isReadyForUserInput = false
            )
        }
        analyzeExam()
    }

    private fun analyzeExam() {
        _uiState.update { it.copy(currentPart = ExamPart.ANALYSIS_COMPLETE, isLoading = true) }
        viewModelScope.launch {
            when (val result = chatRepository.analyzeFullExam(transcript)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    delay(REDIRECT_DELAY_MS)
                    _uiState.update { it.copy(finalResultId = result.data.resultId) }
                }
                is Result.Error -> {
                    Log.e("ExamViewModel", "Analysis failed: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, error = "Analysis failed. Please try again later.") }
                }
            }
        }
    }

    // =================== Helper and System Functions ===================

    private fun processExamStepStream(
        request: ExamStepRequest,
        onStreamEnd: (ExamStreamEndData, String, List<List<Int>>) -> Unit
    ) {
        if (_uiState.value.currentPart >= ExamPart.FINISHED) return

        val ttsChunks = mutableListOf<List<Int>>()
        var fullText = ""

        chatRepository.getNextExamStepStream(request)
            .onEach { event ->
                if (_uiState.value.currentPart >= ExamPart.FINISHED) return@onEach
                when (event) {
                    is ExamEvent.TextChunk -> {
                        fullText += event.text
                        _uiState.update { it.copy(examinerMessage = fullText) }
                    }
                    is ExamEvent.InputIdsChunk -> ttsChunks.add(event.ids)
                    is ExamEvent.StreamEnd -> onStreamEnd(event.endData, fullText, ttsChunks)
                    is ExamEvent.StreamError -> _uiState.update { it.copy(isLoading = false, error = event.message) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun startUserSpeechRecognition() {
        if (recorder?.isRecording == true || isPlayingAudio || _uiState.value.currentPart >= ExamPart.FINISHED) return
        audioDataBuffer.clear()
        _uiState.update { it.copy(isRecording = true, partialTranscription = "", isReadyForUserInput = true) }
        recorder?.start()
    }

    fun stopUserSpeechRecognition() {
        if (recorder?.isRecording != true) return
        if (_uiState.value.currentPart == ExamPart.PART_2_SPEAKING) {
            part2SpeakingTimerJob?.cancel()
        }
        recorder?.stop()
        _uiState.update { it.copy(isRecording = false, isReadyForUserInput = false, partialTranscription = "Processing...") }
    }

    private fun playAudioQueue(ids: List<List<Int>>, onComplete: (() -> Unit)? = null) {
        if (_uiState.value.currentPart >= ExamPart.FINISHED || ids.isEmpty()) {
            onComplete?.invoke() ?: if (_uiState.value.currentPart < ExamPart.FINISHED) {
                startUserSpeechRecognition()
            } else

            return
        }

        isPlayingAudio = true
        _uiState.update { it.copy(isReadyForUserInput = false) }
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