// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/ChatRepository.kt

package com.typosbro.multilevel.data.repositories

import ExamHistorySummaryResponse
import ExamResultSummary
import android.util.Log
import com.google.gson.Gson
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.RetrofitClient
import com.typosbro.multilevel.data.remote.models.*
import kotlinx.coroutines.channels.awaitClose // <-- IMPORTANT: Import awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow // <-- IMPORTANT: Import channelFlow instead of flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSources

/**
 * Repository for handling all chat and exam-related data operations.
 * It communicates with the backend API to fetch and send data.
 */
class ChatRepository(
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient,
    private val tokenProvider: suspend () -> String?
) {
    private val gson = Gson()

    // --- Freestyle Chat Methods (No changes needed here) ---

    suspend fun getChatList(): Result<ChatListResponse> {
        return safeApiCall { apiService.getChatList() }
    }

    suspend fun createNewChat(title: String?): Result<NewChatResponse> {
        return safeApiCall { apiService.createNewChat(NewChatRequest(title)) }
    }

    suspend fun getChatHistory(chatId: String): Result<ChatHistoryResponse> {
        return safeApiCall { apiService.getChatHistory(chatId) }
    }

    suspend fun deleteChat(chatId: String): Result<GenericSuccessResponse> {
        return safeApiCall { apiService.deleteChat(chatId) }
    }

    suspend fun updateChatTitle(chatId: String, newTitle: String): Result<GenericSuccessResponse> {
        return safeApiCall { apiService.updateChatTitle(chatId, TitleUpdateRequest(newTitle)) }
    }

    /**
     * Sends a message in a freestyle chat and streams the response using Server-Sent Events.
     */
    fun sendMessageAndStream(
        chatId: String,
        prompt: String,
        langCodeForTTS: String?,
        configKeyForTTS: String?
    ): Flow<ChatStreamEvent> = channelFlow { // <--- FIX IS HERE: Use channelFlow
        val messageRequest = SendMessageRequest(
            prompt = prompt,
            lang_code = langCodeForTTS,
            config_key = configKeyForTTS
        )
        val jsonBody = gson.toJson(messageRequest)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}api/chat/$chatId/message")
            .header("Authorization", "Bearer ${tokenProvider()}")
            .post(requestBody)
            .build()

        // 'this' inside channelFlow is a ProducerScope, which matches the listener's expectation.
        val listener = SseListenerRepository(this, gson)
        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)

        // awaitClose is the standard way to keep the channelFlow open
        // and provides a block to clean up resources when the flow is cancelled.
        awaitClose {
            eventSource.cancel()
        }
    }


    // --- Structured Exam Methods ---

    suspend fun getInitialExamQuestion(): Result<ExamStepResponse> {
        return safeApiCall { apiService.startExam() }
    }

    /**
     * Gets the next step in the exam by streaming the response.
     */
    fun getNextExamStepStream(request: ExamStepRequest): Flow<ExamEvent> = channelFlow { // <--- FIX IS HERE: Use channelFlow
        Log.d("ChatRepository", "getNextExamStepStream called with request: $request")
        val jsonBody = gson.toJson(request)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url("${RetrofitClient.BASE_URL}exam/step-stream")
            .header("Authorization", "Bearer ${tokenProvider()}")
            .post(requestBody)
            .build()

        // 'this' is a ProducerScope, matching the listener's constructor.
        val listener = ExamSseListener(this, gson)
        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(httpRequest, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    suspend fun analyzeFullExam(transcript: List<TranscriptEntry>): Result<AnalyzeExamResponse> {
        val request = AnalyzeExamRequest(transcript = transcript)
        return safeApiCall { apiService.analyzeExam(request) }
    }

    suspend fun getExamHistorySummary(): Result<ExamHistorySummaryResponse> {
        // ... (mock data implementation is fine)
        val mockHistory = listOf(
            ExamResultSummary("id_1", System.currentTimeMillis() - 86400000L * 5, 6.0),
            ExamResultSummary("id_2", System.currentTimeMillis() - 86400000L * 3, 6.5),
            ExamResultSummary("id_3", System.currentTimeMillis() - 86400000L * 1, 6.5),
        )
        val mockResponse = ExamHistorySummaryResponse(history = mockHistory)
        return Result.Success(mockResponse)
    }

    suspend fun getExamResultDetails(resultId: String): Result<ExamResultResponse> {
        return safeApiCall { apiService.getExamResult(resultId) }
    }
}