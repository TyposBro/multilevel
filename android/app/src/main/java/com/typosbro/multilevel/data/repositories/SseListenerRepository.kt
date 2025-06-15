// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/SseListenerRepository.kt

package com.typosbro.multilevel.data.repositories

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.typosbro.multilevel.data.remote.models.* // Import all the new stream event models
import kotlinx.coroutines.channels.ProducerScope
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

/**
 * A generic SSE Listener for the FREESTYLE CHAT stream.
 * It parses events and emits them into a Flow's ProducerScope.
 */
class SseListenerRepository(
    private val scope: ProducerScope<ChatStreamEvent>,
    private val gson: Gson
) : EventSourceListener() {

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        if (scope.isClosedForSend) return

        try {
            // Parse the data, which might result in a nullable object
            val parsedEvent: ChatStreamEvent? = when (type) {
                "text_chunk" -> gson.fromJson(data, TextChunk::class.java)?.let { ChatStreamEvent.TextChunk(it.text) }
                "input_ids_chunk" -> gson.fromJson(data, InputIdsChunk::class.java)?.let { ChatStreamEvent.InputIdsChunk(it.sentence, it.input_ids) }
                "stream_end" -> gson.fromJson(data, StreamEnd::class.java)?.let { ChatStreamEvent.StreamEnd(it.message) }
                "preprocess_warning" -> gson.fromJson(data, PreprocessWarning::class.java)?.let { ChatStreamEvent.PreprocessWarning(it.message, it.sentenceText) }
                "preprocess_error" -> gson.fromJson(data, PreprocessError::class.java)?.let { ChatStreamEvent.PreprocessError(it.message, it.sentenceText) }
                "error" -> gson.fromJson(data, StreamError::class.java)?.let { ChatStreamEvent.StreamError(it.message, it.details) }
                else -> null
            }

            // --- FIX IS HERE ---
            // Only try to send if the parsed event is not null.
            if (parsedEvent != null) {
                scope.trySend(parsedEvent)
            }
        } catch (e: JsonSyntaxException) {
            scope.trySend(ChatStreamEvent.StreamError("Failed to parse event data: ${e.message}", data))
        }
    }

    override fun onOpen(eventSource: EventSource, response: Response) {}

    override fun onClosed(eventSource: EventSource) {
        scope.close()
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        val errorMessage = t?.message ?: response?.message ?: "Unknown SSE error"
        scope.trySend(ChatStreamEvent.StreamError(errorMessage, response?.body?.string()))
        scope.close(t)
    }
}

/**
 * A specific SSE Listener for the EXAM stream.
 */
class ExamSseListener(
    private val scope: ProducerScope<ExamEvent>,
    private val gson: Gson
) : EventSourceListener() {

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        if (scope.isClosedForSend) return

        try {
            val parsedEvent: ExamEvent? = when (type) {
                "text_chunk" -> gson.fromJson(data, TextChunk::class.java)?.let { ExamEvent.TextChunk(it.text) }
                "input_ids_chunk" -> gson.fromJson(data, InputIdsChunk::class.java)?.let { ExamEvent.InputIdsChunk(it.sentence, it.input_ids) }
                "stream_end" -> gson.fromJson(data, ExamStreamEndData::class.java)?.let { ExamEvent.StreamEnd(it) }
                "error" -> gson.fromJson(data, StreamError::class.java)?.let { ExamEvent.StreamError(it.message) }
                else -> null
            }

            // --- FIX IS HERE ---
            // Only try to send if the parsed event is not null.
            if (parsedEvent != null) {
                scope.trySend(parsedEvent)
            }
        } catch (e: JsonSyntaxException) {
            scope.trySend(ExamEvent.StreamError("Failed to parse event data: ${e.message}"))
        }
    }

    override fun onOpen(eventSource: EventSource, response: Response) {}

    override fun onClosed(eventSource: EventSource) {
        scope.close()
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        val errorMessage = t?.message ?: response?.message ?: "Unknown SSE error during exam"
        scope.trySend(ExamEvent.StreamError(errorMessage))
        scope.close(t)
    }
}