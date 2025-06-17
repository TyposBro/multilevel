// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ExamViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

// TODO: Once frontend moves to the next part of the exam,
//  stop incoming audio inputs/text from backend
//  and make sure to delete the last message/question from backend
//  not to affect the next part of the exam or scoring.

// TODO: Add timer for each part of the exam
// TODO: Add prep time for Part 2
// TODO: Add start/stop recording buttons for each part

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
    private var timerJob: Job? = null

    // IELTS-specific timers for each part
    private var part1TimerJob: Job? = null
    private var part2PrepTimerJob: Job? = null
    private var part2SpeakingTimerJob: Job? = null
    private var part3TimerJob: Job? = null

    private val audioDataBuffer = mutableListOf<FloatArray>()
    private val transcript = mutableListOf<TranscriptEntry>()
    private var questionCountInPart = 0
    private var audioQueue = mutableListOf<List<Int>>()
    private var isPlayingAudio = false

    // IELTS timing constants (in seconds)
    companion object {
        const val PART_1_DURATION = 5 * 60 // 5 minutes (4-5 minutes range, using max)
        const val PART_2_PREP_DURATION = 60 // 1 minute preparation
        const val PART_2_SPEAKING_DURATION = 2 * 60 // 2 minutes speaking time
        const val PART_3_DURATION = 5 * 60 // 5 minutes (4-5 minutes range, using max)
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
        when (_uiState.value.currentPart) {
            ExamPart.PART_1 -> continueWithPart1Questions("")
            ExamPart.PART_2_SPEAKING -> continueWithPart2Speaking("")
            ExamPart.PART_3 -> continueWithPart3Questions("")
            else -> { /* Do nothing for prep phases */ }
        }
    }

    private fun handleTranscriptionResult(transcription: String) {
        when (_uiState.value.currentPart) {
            ExamPart.PART_1 -> continueWithPart1Questions(transcription)
            ExamPart.PART_2_SPEAKING -> continueWithPart2Speaking(transcription)
            ExamPart.PART_3 -> continueWithPart3Questions(transcription)
            else -> getNextExamStep(transcription) // Fallback for other parts
        }
    }

    // =================== IELTS PART 1: Introduction and Interview (4-5 minutes) ===================
    fun startExam() {
        if (_uiState.value.isLoading) return
        questionCountInPart = 0
        transcript.clear()
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
            // Show countdown timer for Part 1
            for (i in PART_1_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            // Time's up for Part 1 - move to Part 2
            if (_uiState.value.currentPart == ExamPart.PART_1) {
                Log.d("ExamViewModel", "Part 1 completed (5 minutes), moving to Part 2")
                recorder?.stop()
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

        processExamStepStream(request) { endData, fullText, ttsChunks ->
            // Always stay in Part 1 until timer expires
            if (fullText.isNotBlank()) {
                transcript.add(TranscriptEntry("Examiner", fullText))
            }
            _uiState.update { it.copy(isLoading = false) }
            playAudioQueue(ttsChunks)
        }
    }

    // =================== IELTS PART 2: Long Turn (3-4 minutes total) ===================
    // In the moveToPart2() function, ensure proper state management:
    private fun moveToPart2() {
        part1TimerJob?.cancel()
        questionCountInPart = 0

        _uiState.update {
            it.copy(
                currentPart = ExamPart.PART_2_PREP,
                examinerMessage = "That's the end of Part 1. Now we'll move to Part 2. I'll give you a topic and you'll have 1 minute to prepare.",
                isLoading = true,
                timerValue = 0,
                isRecording = false, // Stop recording from Part 1
                isReadyForUserInput = false, // Not ready for input during prep
                partialTranscription = "" // Clear previous transcription
            )
        }

        viewModelScope.launch {
            val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
                part = 2,
                userInput = "Moving to Part 2",
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

                // Play audio then start 1-minute preparation timer
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
            // Preparation time over - start speaking
            _uiState.update {
                it.copy(
                    currentPart = ExamPart.PART_2_SPEAKING,
                    examinerMessage = "Your preparation time is over. Please start speaking now. You have 2 minutes.",
                    timerValue = 0
                )
            }
            startPart2SpeakingTimer()
            // Automatically start recording for Part 2 speaking
            startUserSpeechRecognition()
        }
    }

    // In the startPart2SpeakingTimer() function, ensure proper recording state:
    private fun startPart2SpeakingTimer() {
        part2SpeakingTimerJob?.cancel()
        part2SpeakingTimerJob = viewModelScope.launch {
            for (i in PART_2_SPEAKING_DURATION downTo 1) {
                _uiState.update { it.copy(timerValue = i) }
                delay(1000)
            }
            // 2 minutes speaking time over
            recorder?.stop()
            _uiState.update {
                it.copy(
                    examinerMessage = "Thank you. That's the end of your talk.",
                    timerValue = 0,
                    isRecording = false,
                    isReadyForUserInput = false
                )
            }
            // Brief follow-up questions, then move to Part 3
            delay(2000) // Brief pause
            moveToPart3()
        }
    }
    private fun continueWithPart2Speaking(userInput: String) {
        // For Part 2, we mainly just collect the long turn speech
        // Don't ask follow-up questions during the 2-minute speaking time
        if (userInput.isNotBlank()) {
            transcript.add(TranscriptEntry("User", userInput))
        }
        _uiState.update { it.copy(partialTranscription = "") }
        // Continue recording until timer expires
        if (_uiState.value.currentPart == ExamPart.PART_2_SPEAKING) {
            startUserSpeechRecognition()
        }
    }

    // =================== IELTS PART 3: Discussion (4-5 minutes) ===================
    // In the moveToPart3() function, ensure proper state management:
    private fun moveToPart3() {
        part2SpeakingTimerJob?.cancel()
        questionCountInPart = 0

        _uiState.update {
            it.copy(
                currentPart = ExamPart.PART_3,
                examinerMessage = "Now let's move to Part 3. We'll discuss some more abstract questions related to the topic.",
                isLoading = true,
                timerValue = 0,
                isRecording = false,
                isReadyForUserInput = false,
                partialTranscription = "",
                part2CueCard = null // Clear cue card for Part 3
            )
        }

        startPart3Timer()

        viewModelScope.launch {
            val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
                part = 3,
                userInput = "Moving to Part 3",
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
            // Part 3 completed - end exam
            if (_uiState.value.currentPart == ExamPart.PART_3) {
                Log.d("ExamViewModel", "Part 3 completed (5 minutes), ending exam")
                recorder?.stop()
                endExam()
            }
        }
    }

    private fun continueWithPart3Questions(userInput: String) {
        _uiState.update { it.copy(isLoading = true, partialTranscription = "") }
        questionCountInPart++

        val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
            part = 3,
            userInput = userInput,
            transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCountInPart
        )

        processExamStepStream(request) { endData, fullText, ttsChunks ->
            // Always stay in Part 3 until timer expires
            if (fullText.isNotBlank()) {
                transcript.add(TranscriptEntry("Examiner", fullText))
            }
            _uiState.update { it.copy(isLoading = false) }
            playAudioQueue(ttsChunks)
        }
    }

    private fun endExam() {
        cancelAllTimers()
        _uiState.update {
            it.copy(
                currentPart = ExamPart.FINISHED,
                examinerMessage = "That's the end of the IELTS Speaking Test. Thank you.",
                timerValue = 0
            )
        }
        analyzeExam()
    }

    // =================== HELPER FUNCTIONS ===================
    private fun processExamStepStream(
        request: com.typosbro.multilevel.data.remote.models.ExamStepRequest,
        onStreamEnd: (ExamStreamEndData, String, List<List<Int>>) -> Unit
    ) {
        val ttsChunks = mutableListOf<List<Int>>()
        var fullText = ""

        chatRepository.getNextExamStepStream(request)
            .onEach { event ->
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



    // Update startUserSpeechRecognition to properly set state:
    fun startUserSpeechRecognition() {
        if (recorder?.isRecording == true || isPlayingAudio) return
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
        recorder?.stop()
        _uiState.update { it.copy(isRecording = false, isReadyForUserInput = false, partialTranscription = "Processing...") }
    }

    // Fix the playAudioQueue completion behavior:
    private fun playAudioQueue(ids: List<List<Int>>, onComplete: (() -> Unit)? = null) {
        if (ids.isEmpty()) {
            onComplete?.invoke() ?: run {
                // Only auto-start recording for parts that need user input
                when (_uiState.value.currentPart) {
                    ExamPart.PART_1, ExamPart.PART_3 -> startUserSpeechRecognition()
                    ExamPart.PART_2_SPEAKING -> startUserSpeechRecognition()
                    else -> {
                        // For PART_2_PREP, don't start recording
                        _uiState.update { it.copy(isReadyForUserInput = false) }
                    }
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
        if (audioQueue.isEmpty()) {
            isPlayingAudio = false
            onComplete?.invoke() ?: startUserSpeechRecognition()
            return
        }
        viewModelScope.launch {
            val audioBytes = AudioPlayer.createAudioAndConvertToWav(audioQueue.removeAt(0).map { it.toLong() }, context)
            AudioPlayer.playAudio(context, audioBytes) { playNextAudioInQueue(onComplete) }
        }
    }

    // Legacy function for backward compatibility (Parts 2 prep and other edge cases)
    private fun getNextExamStep(userInput: String) {
        _uiState.update { it.copy(isLoading = true, partialTranscription = "") }
        questionCountInPart++

        val request = com.typosbro.multilevel.data.remote.models.ExamStepRequest(
            part = _uiState.value.currentPart.ordinal,
            userInput = userInput,
            transcriptContext = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCountInPart
        )

        processExamStepStream(request) { endData, fullText, ttsChunks ->
            handleStateTransition(endData, fullText, ttsChunks)
        }
    }

    private fun handleStateTransition(endData: ExamStreamEndData, fullText: String, ttsChunks: List<List<Int>>) {
        if (fullText.isNotBlank()) {
            transcript.add(TranscriptEntry("Examiner", fullText))
        }
        questionCountInPart = if (endData.next_part != _uiState.value.currentPart.ordinal) 0 else questionCountInPart
        val nextPart = ExamPart.entries.getOrNull(endData.next_part) ?: _uiState.value.currentPart

        _uiState.update {
            it.copy(
                isLoading = false,
                currentPart = nextPart,
                part2CueCard = endData.cue_card
            )
        }

        when (nextPart) {
            ExamPart.FINISHED -> analyzeExam()
            else -> playAudioQueue(ttsChunks)
        }
    }

    private fun analyzeExam() {
        _uiState.update { it.copy(currentPart = ExamPart.ANALYSIS_COMPLETE, isLoading = true) }
        viewModelScope.launch {
            when (val result = chatRepository.analyzeFullExam(transcript)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, finalResultId = result.data.resultId) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    private fun cancelAllTimers() {
        part1TimerJob?.cancel()
        part2PrepTimerJob?.cancel()
        part2SpeakingTimerJob?.cancel()
        part3TimerJob?.cancel()
        timerJob?.cancel()
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