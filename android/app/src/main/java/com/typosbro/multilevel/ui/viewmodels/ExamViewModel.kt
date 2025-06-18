// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ExamViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.CueCard
import com.typosbro.multilevel.data.remote.models.ExamEvent
import com.typosbro.multilevel.data.remote.models.ExamStreamEndData
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
import kotlin.collections.ArrayList

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
// Modified ExamViewModel.kt - IELTS Speaking Test Format (11-14 minutes total)

@HiltViewModel
class ExamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository
) : ViewModel(), Recorder.RecorderListener {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState = _uiState.asStateFlow()

    private var whisperEngine: WhisperEngineNative? = null
    private var recorder: Recorder? = null

    // IELTS-specific timers for each part
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

    // IELTS timing constants (in seconds)
    companion object {
        const val PART_1_DURATION = 30 // 5 minutes (4-5 minutes range, using max)
        const val PART_2_PREP_DURATION = 10 // 1 minute preparation
        const val PART_2_SPEAKING_DURATION = 30 // 2 minutes speaking time
        const val PART_3_DURATION = 30 // 5 minutes (4-5 minutes range, using max)
        const val FINAL_ANSWER_WINDOW_S = 10 // Last 10 seconds of Part 3
        const val REDIRECT_DELAY_MS = 3000L // 3-second delay before redirecting
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
                handleEmptyTranscription()
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

    private fun handleEmptyTranscription() {
        transcript.add(TranscriptEntry("User", ""))
        handleTranscriptionResult("")
    }

    private fun handleTranscriptionResult(transcription: String) {
        if (isExamFinishedByTimer.get()) return

        when (_uiState.value.currentPart) {
            ExamPart.PART_1 -> continueWithPart1Questions(transcription)
            ExamPart.PART_2_SPEAKING -> continueWithPart2Speaking(transcription)
            ExamPart.PART_3 -> continueWithPart3Questions(transcription)
            else -> { /* Do nothing for other states like prep */ }
        }
    }

    fun onResultNavigationConsumed() {
        _uiState.update { it.copy(finalResultId = null) }
    }

    // =================== IELTS PART 1: Introduction and Interview (4-5 minutes) ===================
    fun startExam() {
        if (_uiState.value.isLoading) return
        questionCountInPart = 0
        transcript.clear()
        isExamFinishedByTimer.set(false)
        _uiState.update {
            it.copy(
                isLoading = true,
                currentPart = ExamPart.PART_1,
                examinerMessage = "Starting IELTS Speaking Test - Part 1: Introduction and Interview..."
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
        part1TimerJob?.cancel()
        part1TimerJob = viewModelScope.launch {
            for (i in PART_1_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            if (_uiState.value.currentPart == ExamPart.PART_1) {
                Log.d("ExamViewModel", "Part 1 timer finished. Moving to Part 2.")
                if (_uiState.value.isRecording) recorder?.stop()
                moveToPart2()
            }
        }
    }

    private fun continueWithPart1Questions(userInput: String) {
        _uiState.update { it.copy(isLoading = true, partialTranscription = "") }
        questionCountInPart++

        val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
            part = 1,
            userInput = userInput,
            transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCountInPart
        )

        processExamStepStream(request) { _, fullText, ttsChunks ->
            if (fullText.isNotBlank()) {
                transcript.add(TranscriptEntry("Examiner", fullText))
            }
            _uiState.update { it.copy(isLoading = false) }
            playAudioQueue(ttsChunks)
        }
    }

    // =================== IELTS PART 2: Long Turn (3-4 minutes total) ===================
    private fun moveToPart2() {
        part1TimerJob?.cancel()
        questionCountInPart = 0

        _uiState.update {
            it.copy(
                currentPart = ExamPart.PART_2_PREP,
                examinerMessage = "Moving to Part 2...",
                isLoading = true,
                timerValue = 0,
                isRecording = false,
                isReadyForUserInput = false,
                partialTranscription = ""
            )
        }

        viewModelScope.launch {
            val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
                part = 2,
                userInput = "Moving to Part 2. Provide the standard instructions for Part 2, but DO NOT mention the topic itself in your spoken line.",
                transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
                questionCountInPart = 0
            )

            processExamStepStream(request) { endData, fullText, ttsChunks ->
                if (fullText.isNotBlank()) {
                    transcript.add(TranscriptEntry("Examiner", fullText))
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPart = ExamPart.PART_2_PREP,
                        part2CueCard = endData.cue_card,
                        examinerMessage = fullText
                    )
                }
                playAudioQueue(ttsChunks) {
                    startPart2PrepTimer()
                }
            }
        }
    }
    private fun startPart2PrepTimer() {
        part2PrepTimerJob?.cancel()
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
        part2SpeakingTimerJob?.cancel()
        part2SpeakingTimerJob = viewModelScope.launch {
            for (i in PART_2_SPEAKING_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            if (_uiState.value.isRecording) {
                recorder?.stop()
            } else {
                moveToPart3()
            }
        }
    }

    private fun continueWithPart2Speaking(userInput: String) {
        _uiState.update { it.copy(partialTranscription = "") }

        if (part2SpeakingTimerJob?.isCompleted == true) {
            moveToPart3()
        } else if (_uiState.value.currentPart == ExamPart.PART_2_SPEAKING) {
            startUserSpeechRecognition()
        }
    }

    // =================== IELTS PART 3: Discussion (4-5 minutes) ===================
    private fun moveToPart3() {
        part2SpeakingTimerJob?.cancel()
        questionCountInPart = 0

        _uiState.update {
            it.copy(
                currentPart = ExamPart.PART_3,
                examinerMessage = "Thank you.",
                isLoading = true,
                timerValue = 0,
                isRecording = false,
                isReadyForUserInput = false,
                partialTranscription = "",
                part2CueCard = null
            )
        }

        startPart3Timer()

        viewModelScope.launch {
            val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
                part = 3,
                userInput = "The user has just finished their Part 2 talk. Say 'Thank you' and then begin Part 3 by asking the first discussion question.",
                transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
                questionCountInPart = 0
            )

            processExamStepStream(request) { _, fullText, ttsChunks ->
                if (fullText.isNotBlank()) {
                    transcript.add(TranscriptEntry("Examiner", fullText))
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPart = ExamPart.PART_3,
                        examinerMessage = fullText
                    )
                }
                playAudioQueue(ttsChunks)
            }
        }
    }

    private fun startPart3Timer() {
        part3TimerJob?.cancel()
        part3TimerJob = viewModelScope.launch {
            for (i in PART_3_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            if (_uiState.value.currentPart == ExamPart.PART_3 && !isExamFinishedByTimer.get()) {
                Log.d("ExamViewModel", "Part 3 timer finished. Ending exam.")
                isExamFinishedByTimer.set(true)
                if (_uiState.value.isRecording) recorder?.stop()
                endExam()
            }
        }
    }

    private fun continueWithPart3Questions(userInput: String) {
        if (isExamFinishedByTimer.get()) return

        _uiState.update { it.copy(isLoading = true, partialTranscription = "") }
        questionCountInPart++

        if (_uiState.value.currentPart == ExamPart.PART_3 && _uiState.value.timerValue <= FINAL_ANSWER_WINDOW_S) {
            Log.d("ExamViewModel", "Final answer received. Concluding exam.")
            isExamFinishedByTimer.set(true)
            part3TimerJob?.cancel()

            val finalUserInput = "This is the user's final answer: \"$userInput\". Please respond by saying 'Thank you, that's the end of the IELTS Speaking Test. You will receive your results in a few moments.' and nothing else."
            val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
                part = 3,
                userInput = finalUserInput,
                transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
                questionCountInPart = 99
            )

            processExamStepStream(request) { _, fullText, ttsChunks ->
                if (fullText.isNotBlank()) {
                    transcript.add(TranscriptEntry("Examiner", fullText))
                }
                playAudioQueue(ttsChunks) {
                    analyzeExam()
                }
            }
        } else {
            val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
                part = 3,
                userInput = userInput,
                transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
                questionCountInPart = questionCountInPart
            )

            processExamStepStream(request) { _, fullText, ttsChunks ->
                if (fullText.isNotBlank()) {
                    transcript.add(TranscriptEntry("Examiner", fullText))
                }
                _uiState.update { it.copy(isLoading = false) }
                playAudioQueue(ttsChunks)
            }
        }
    }

    private fun endExam() {
        cancelAllTimers()
        val finalMessage = "Thank you, that's the end of the IELTS Speaking Test. You will receive your results in a few moments."
        val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
            part = 3, userInput = finalMessage, transcriptContext = "", questionCountInPart = 99
        )
        _uiState.update {
            it.copy(
                examinerMessage = finalMessage,
                timerValue = 0,
                isRecording = false,
                isReadyForUserInput = false
            )
        }
        processExamStepStream(request) { _, _, ttsChunks ->
            transcript.add(TranscriptEntry("Examiner", finalMessage))
            playAudioQueue(ttsChunks) {
                analyzeExam()
            }
        }
    }

    // =================== HELPER FUNCTIONS ===================
    private fun processExamStepStream(
        request: com.typosbro.multilevel.data.remote.models.ExamStepRequest,
        onStreamEnd: (ExamStreamEndData, String, List<List<Int>>) -> Unit
    ) {
        if (isExamFinishedByTimer.get() && request.questionCountInPart != 99) return
        val ttsChunks = mutableListOf<List<Int>>()
        var fullText = ""

        chatRepository.getNextExamStepStream(request)
            .onEach { event ->
                if (isExamFinishedByTimer.get() && request.questionCountInPart != 99) return@onEach
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
        if (recorder?.isRecording == true || isPlayingAudio || isExamFinishedByTimer.get()) return
        audioDataBuffer.clear()
        _uiState.update {
            it.copy(
                isRecording = true,
                partialTranscription = "",
                isReadyForUserInput = true
            )
        }
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
        if (isExamFinishedByTimer.get() && onComplete == null) return
        if (ids.isEmpty()) {
            onComplete?.invoke() ?: run {
                when (_uiState.value.currentPart) {
                    ExamPart.PART_1, ExamPart.PART_3, ExamPart.PART_2_SPEAKING -> startUserSpeechRecognition()
                    else -> _uiState.update { it.copy(isReadyForUserInput = false) }
                }
            }
            return
        }
        isPlayingAudio = true
        _uiState.update { it.copy(isReadyForUserInput = false) }
        audioQueue.clear()
        audioQueue.addAll(ids)
        playNextAudioInQueue(onComplete)
    }

    private fun playNextAudioInQueue(onComplete: (() -> Unit)? = null) {
        if (isExamFinishedByTimer.get() && onComplete == null) {
            audioQueue.clear()
            isPlayingAudio = false
            return
        }
        if (audioQueue.isEmpty()) {
            isPlayingAudio = false
            val currentPart = _uiState.value.currentPart
            if (onComplete != null) {
                onComplete()
            } else if (currentPart == ExamPart.PART_1 || currentPart == ExamPart.PART_3 || currentPart == ExamPart.PART_2_SPEAKING) {
                startUserSpeechRecognition()
            }
            return
        }
        viewModelScope.launch {
            val audioBytes = AudioPlayer.createAudioAndConvertToWav(audioQueue.removeAt(0).map { it.toLong() }, context)
            AudioPlayer.playAudio(context, audioBytes) { playNextAudioInQueue(onComplete) }
        }
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
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
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