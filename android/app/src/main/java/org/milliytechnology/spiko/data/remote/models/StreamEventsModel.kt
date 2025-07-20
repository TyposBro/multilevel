// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/StreamEventsModel.kt
package org.milliytechnology.spiko.data.remote.models

// --- Data classes for parsing JSON from SSE events ---
// These are shared between freestyle chat and exam streams

data class TextChunk(val text: String)
data class InputIdsChunk(val sentence: String, val input_ids: List<Int>)
data class StreamEnd(val message: String) // For simple stream end
data class PreprocessWarning(val message: String, val sentenceText: String?)
data class PreprocessError(val message: String, val sentenceText: String?)
data class StreamError(val message: String, val details: String?)

// --- Sealed class for FREESTYLE CHAT events ---

sealed class ChatStreamEvent {
    data class TextChunk(val text: String) : ChatStreamEvent()
    data class InputIdsChunk(val sentence: String, val ids: List<Int>) : ChatStreamEvent()
    data class StreamEnd(val message: String) : ChatStreamEvent()
    data class PreprocessWarning(val message: String, val sentenceText: String?) : ChatStreamEvent()
    data class PreprocessError(val message: String, val sentenceText: String?) : ChatStreamEvent()
    data class StreamError(val message: String, val details: String?) : ChatStreamEvent()
}


// --- Sealed class for EXAM events ---

// Data class specific to the end of an EXAM stream
data class ExamStreamEndData(
    val next_part: Int,
    val cue_card: CueCard?,
    val is_final_question: Boolean
)

sealed class ExamEvent {
    data class TextChunk(val text: String) : ExamEvent()
    data class InputIdsChunk(val sentence: String, val ids: List<Int>) : ExamEvent()
    data class StreamEnd(val endData: ExamStreamEndData) : ExamEvent()
    data class StreamError(val message: String) : ExamEvent()
}