// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/ChatRepository.kt
package com.typosbro.multilevel.data.repositories


import android.util.Log
import com.google.gson.Gson
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.sse.EventSources
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatRepository(
    private val apiService: ApiService,
    private val sseClient: OkHttpClient // Use the dedicated SSE client
) {
    private val gson = Gson()

    // --- Freestyle Chat Methods ---
    suspend fun getChatList(): Result<ChatListResponse> = safeApiCall { apiService.getChatList() }
    suspend fun createNewChat(title: String?): Result<NewChatResponse> = safeApiCall { apiService.createNewChat(NewChatRequest(title)) }
    suspend fun getChatHistory(chatId: String): Result<ChatHistoryResponse> = safeApiCall { apiService.getChatHistory(chatId) }
    suspend fun deleteChat(chatId: String): Result<GenericSuccessResponse> = safeApiCall { apiService.deleteChat(chatId) }
    suspend fun updateChatTitle(chatId: String, newTitle: String): Result<GenericSuccessResponse> = safeApiCall { apiService.updateChatTitle(chatId, TitleUpdateRequest(newTitle)) }

    /**
     * Sends a message and streams the response. Now uses ApiService.
     */
    fun sendMessageAndStream(
        chatId: String,
        prompt: String,
        langCodeForTTS: String?,
        configKeyForTTS: String?
    ): Flow<ChatStreamEvent> = channelFlow {
        val request = SendMessageRequest(
            prompt = prompt,
            lang_code = langCodeForTTS,
            config_key = configKeyForTTS
        )
        val call = apiService.sendMessageAndStream(chatId, request)
        val listener = SseListenerRepository(this, gson)
        // Use the dedicated SSE client for the EventSource
        val eventSource = EventSources.createFactory(sseClient).newEventSource(call.request(), listener)

        awaitClose { eventSource.cancel() }
    }

    // --- Structured Exam Methods ---
    suspend fun getInitialExamQuestion(): Result<ExamStepResponse> = safeApiCall { apiService.startExam() }

    /**
     * Gets the next step in the exam by streaming. Now uses ApiService.
     */
    fun getNextExamStepStream(request: ExamStepRequest): Flow<ExamEvent> = channelFlow {
        Log.d("ChatRepository", "getNextExamStepStream called with request: $request")

        // Create the Retrofit call via the ApiService
        val call = apiService.getNextExamStepStream(request)

        // The listener remains the same, handling the parsed events
        val listener = ExamSseListener(this, gson)

        // Use the dedicated SSE OkHttpClient to create the EventSource
        val eventSource = EventSources.createFactory(sseClient).newEventSource(call.request(), listener)

        // This ensures the connection stays open until the Flow is cancelled
        awaitClose {
            Log.d("ChatRepository", "Closing SSE connection for exam stream.")
            eventSource.cancel()
        }
    }

    suspend fun analyzeFullExam(transcript: List<TranscriptEntry>): Result<AnalyzeExamResponse> {
        val request = AnalyzeExamRequest(transcript = transcript)
        return safeApiCall { apiService.analyzeExam(request) }
    }

    suspend fun getExamHistorySummary(): Result<ExamHistorySummaryResponse> = safeApiCall { apiService.getExamHistory() }
    suspend fun getExamResultDetails(resultId: String): Result<ExamResultResponse> = safeApiCall { apiService.getExamResult(resultId) }
}