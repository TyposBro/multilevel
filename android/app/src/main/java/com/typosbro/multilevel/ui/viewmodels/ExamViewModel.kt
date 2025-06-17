// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ExamViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.R
import com.typosbro.multilevel.data.remote.models.*
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import com.typosbro.multilevel.features.whisper.Recorder
import com.typosbro.multilevel.features.whisper.Whisper
import com.typosbro.multilevel.utils.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

// --- Data classes remain the same ---
enum class ExamPart { NOT_STARTED, PART_1, PART_2_PREP, PART_2_SPEAKING, PART_3, FINISHED, ANALYSIS_COMPLETE }

data class ExamUiState(
    val currentPart: ExamPart = ExamPart.NOT_STARTED,
    val examinerMessage: String? = null,
    val isExaminerSpeaking: Boolean = false,
    val isRecording: Boolean = false, // Changed from isUserListening
    val partialTranscription: String = "",
    val part2CueCard: CueCard? = null,
    val timerValue: Int = 0,
    val isModelReady: Boolean = false,
    val error: String? = null,
    val finalResultId: String? = null
)

class ExamViewModel(
    application: Application,
    private val chatRepository: ChatRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState = _uiState.asStateFlow()

    // --- Core Exam State ---
    private val fullTranscript = mutableListOf<TranscriptEntry>()
    private var isFinalQuestion = false
    private var timerJob: Job? = null
    private var textStreamHasEnded = false
    private var questionCounter = 0

    // --- Whisper STT State ---
    private var whisper: Whisper? = null
    private var recorder: Recorder? = null
    private var accumulatedTranscriptionBuffer = ""

    // --- TTS & Audio Queuing System ---
    private var accumulatedTextForStream: String = ""
    private val ttsSynthesisRequestChannel = Channel<List<Long>>(Channel.UNLIMITED)
    private val audioPlaybackDataChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val audioPlaybackQueue: Queue<ByteArray> = LinkedList()
    private var isCurrentlyPlayingAudio = false

    init {
        initWhisper()
        initTtsAndAudioPlayer()
    }

    // --- Public Functions (Called from UI) ----
    fun startExam() {
        _uiState.update { it.copy(currentPart = ExamPart.PART_1, error = null) }
        fullTranscript.clear()
        isFinalQuestion = false
        questionCounter = 0 // Reset counter

        viewModelScope.launch {
            when (val result = chatRepository.getInitialExamQuestion()) {
                is Result.Success -> {
                    val response = result.data
                    fullTranscript.add(TranscriptEntry("Examiner", response.examinerLine))
                    _uiState.update {
                        it.copy(
                            examinerMessage = response.examinerLine,
                            isExaminerSpeaking = true
                        )
                    }
                    synthesizeAndPlaySingle(response.inputIds?.map { it.toLong() })
                }
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun startUserSpeechRecognition() {
        if (!uiState.value.isModelReady || recorder == null || uiState.value.isRecording) return
        accumulatedTranscriptionBuffer = ""
        _uiState.update { it.copy(isRecording = true, partialTranscription = "") }
        recorder?.start()
    }

    fun stopUserSpeechRecognition() {
        if (!uiState.value.isRecording) return
        _uiState.update { it.copy(isRecording = false) }
        recorder?.stop()
    }

    // --- Main Logic (Triggered by Whisper/Recorder Listeners) ---
    private fun getNextExamStep(userTranscription: String) {
        if (userTranscription.isBlank()) {
            if (!isFinalQuestion) startUserSpeechRecognition()
            return
        }

        fullTranscript.add(TranscriptEntry("User", userTranscription))
        if (isFinalQuestion) {
            endExamAndAnalyze()
            return
        }

        _uiState.update { it.copy(isExaminerSpeaking = true, examinerMessage = "...") }
        accumulatedTextForStream = ""
        textStreamHasEnded = false
        audioPlaybackQueue.clear()
        isCurrentlyPlayingAudio = false
        AudioPlayer.stopPlayback()

        val request = ExamStepRequest(
            part = uiState.value.currentPart.ordinal,
            userInput = userTranscription,
            transcriptContext = fullTranscript.joinToString("\n") { "${it.speaker}: ${it.text}" },
            questionCountInPart = questionCounter
        )

        chatRepository.getNextExamStepStream(request)
            .onEach { event ->
                when (event) {
                    is ExamEvent.TextChunk -> {
                        accumulatedTextForStream += event.text
                        _uiState.update { it.copy(examinerMessage = accumulatedTextForStream) }
                    }
                    is ExamEvent.InputIdsChunk -> {
                        if (event.ids.isNotEmpty()) {
                            ttsSynthesisRequestChannel.send(event.ids.map { it.toLong() })
                        }
                    }
                    is ExamEvent.StreamEnd -> {
                        textStreamHasEnded = true
                        fullTranscript.add(TranscriptEntry("Examiner", accumulatedTextForStream))

                        val currentPartOrdinal = uiState.value.currentPart.ordinal
                        if (currentPartOrdinal != event.endData.next_part) {
                            questionCounter = 0
                        }

                        _uiState.update {
                            it.copy(
                                currentPart = ExamPart.entries[event.endData.next_part],
                                part2CueCard = event.endData.cue_card
                            )
                        }
                        isFinalQuestion = event.endData.is_final_question

                        if (audioPlaybackQueue.isEmpty() && !isCurrentlyPlayingAudio) {
                            handleExaminerSpeechFinished()
                        }
                    }
                    is ExamEvent.StreamError -> {
                        _uiState.update { it.copy(error = event.message, isExaminerSpeaking = false) }
                    }
                }
            }
            .catch { e ->
                Log.e("ExamViewModel", "Error collecting stream", e)
                _uiState.update { it.copy(error = e.message, isExaminerSpeaking = false) }
            }
            .launchIn(viewModelScope)
    }

    // --- Timers & Final Analysis ---
    private fun startPart2PrepTimer() {
        countdownTimer(60) {
            _uiState.update { it.copy(currentPart = ExamPart.PART_2_SPEAKING) }
            val prepTimeUpLine = "Your preparation time is up. Please start speaking now."
            fullTranscript.add(TranscriptEntry("Examiner", prepTimeUpLine))
            _uiState.update { it.copy(examinerMessage = prepTimeUpLine, isExaminerSpeaking = true) }
            synthesizeAndPlaySingle(emptyList())
        }
    }

    private fun startPart2SpeakingTimer() = countdownTimer(120) { stopUserSpeechRecognition() }

    private fun endExamAndAnalyze() {
        _uiState.update { it.copy(currentPart = ExamPart.FINISHED) }
        viewModelScope.launch {
            when (val result = chatRepository.analyzeFullExam(fullTranscript)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            finalResultId = result.data.resultId,
                            currentPart = ExamPart.ANALYSIS_COMPLETE
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun countdownTimer(durationSeconds: Int, onFinish: () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(timerValue = durationSeconds) }
            for (i in durationSeconds downTo 1) {
                if (!isActive) return@launch
                delay(1000)
                _uiState.update { it.copy(timerValue = it.timerValue - 1) }
            }
            onFinish()
        }
    }

    // --- Whisper STT Implementation ---
    private fun initWhisper() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelFile = copyRawResourceToFile("whisper-tiny.en.tflite", R.raw.whisper_tiny_en)
                val vocabFile = copyRawResourceToFile("filters_vocab_en.bin", R.raw.filters_vocab_en)

                if (modelFile == null || vocabFile == null) throw IOException("Failed to setup model files")

                whisper = Whisper(getApplication()).apply {
                    setListener(whisperListener)
                    loadModel(modelFile, vocabFile, false)
                }
                recorder = Recorder(getApplication()).apply { setListener(recorderListener) }

                _uiState.update { it.copy(isModelReady = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to init STT: ${e.message}") }
            }
        }
    }

    private fun copyRawResourceToFile(fileName: String, resourceId: Int): File? {
        val context = getApplication<Application>()
        val file = File(context.cacheDir, fileName)
        if (file.exists()) return file
        try {
            context.resources.openRawResource(resourceId).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            return file
        } catch (e: Exception) {
            Log.e("ExamViewModel", "Error copying raw resource", e)
            return null
        }
    }

    private val recorderListener = object : Recorder.RecorderListener {
        override fun onUpdateReceived(message: String?) {
            if (message == Recorder.MSG_RECORDING_DONE) {
                getNextExamStep(accumulatedTranscriptionBuffer)
                accumulatedTranscriptionBuffer = ""
            }
        }
        override fun onDataReceived(samples: FloatArray?) {
            samples?.let { whisper?.writeBuffer(it) }
        }
    }

    private val whisperListener = object : Whisper.WhisperListener {
        override fun onUpdateReceived(message: String?) { /* For logging or showing "Processing..." */ }

        override fun onResultReceived(result: String?) {
            if (!result.isNullOrBlank()) {
                accumulatedTranscriptionBuffer += result
                _uiState.update { it.copy(partialTranscription = accumulatedTranscriptionBuffer) }
            }
        }
    }

    // --- TTS & Audio Player System Implementation ---
    private fun initTtsAndAudioPlayer() {
        viewModelScope.launch { OnnxRuntimeManager.initialize(getApplication()) }
        observeTTSSynthesisRequests()
        observeAudioPlaybackRequests()
    }

    private fun observeTTSSynthesisRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            ttsSynthesisRequestChannel.consumeAsFlow().collect { sentenceInputIds ->
                try {
                    val audioWavByteArray = AudioPlayer.createAudioAndConvertToWav(sentenceInputIds, getApplication())
                    audioPlaybackDataChannel.send(audioWavByteArray)
                } catch (e: Exception) { Log.e("ExamTTS_Synth", "Error during TTS synthesis", e) }
            }
        }
    }

    private fun observeAudioPlaybackRequests() {
        viewModelScope.launch(Dispatchers.Main) {
            audioPlaybackDataChannel.receiveAsFlow().collect { audioData ->
                audioPlaybackQueue.offer(audioData)
                playNextAudioInQueue()
            }
        }
    }

    private fun playNextAudioInQueue() {
        if (isCurrentlyPlayingAudio || audioPlaybackQueue.isEmpty()) return

        isCurrentlyPlayingAudio = true
        val audioDataToPlay = audioPlaybackQueue.poll()

        if (audioDataToPlay != null) {
            AudioPlayer.playAudio(getApplication(), audioDataToPlay) {
                viewModelScope.launch(Dispatchers.Main) {
                    isCurrentlyPlayingAudio = false
                    playNextAudioInQueue()
                    if (audioPlaybackQueue.isEmpty() && textStreamHasEnded) {
                        handleExaminerSpeechFinished()
                    }
                }
            }
        } else {
            isCurrentlyPlayingAudio = false
        }
    }

    private fun synthesizeAndPlaySingle(inputIds: List<Long>?) {
        if (inputIds.isNullOrEmpty()) {
            handleExaminerSpeechFinished()
            return
        }
        viewModelScope.launch {
            val audioBytes = AudioPlayer.createAudioAndConvertToWav(inputIds, getApplication())
            AudioPlayer.playAudio(getApplication(), audioBytes) {
                handleExaminerSpeechFinished()
            }
        }
    }

    private fun handleExaminerSpeechFinished() {
        Log.d("ExamFlow", "All examiner audio has finished playing for this turn.")
        questionCounter++
        Log.d("ExamFlow", "Question counter incremented to: $questionCounter")

        _uiState.update { it.copy(isExaminerSpeaking = false) }

        if (isFinalQuestion) {
            endExamAndAnalyze()
        } else if (_uiState.value.currentPart == ExamPart.PART_2_PREP) {
            startPart2PrepTimer()
        } else {
            startUserSpeechRecognition()
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder?.stop()
        whisper?.unloadModel()
        timerJob?.cancel()
        ttsSynthesisRequestChannel.close()
        audioPlaybackDataChannel.close()
        AudioPlayer.release()
    }
}