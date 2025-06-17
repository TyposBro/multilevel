// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/ChatRepository.kt
package com.typosbro.multilevel.data.repositories

import com.google.gson.Gson
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.RetrofitClient
import com.typosbro.multilevel.data.remote.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient, // Hilt will provide the correct named instance
    private val tokenManager: TokenManager
) {
    private val gson = Gson()

    // --- Freestyle Chat Methods ---

    suspend fun getChatList(): Result<ChatListResponse> = safeApiCall { apiService.getChatList() }

    suspend fun createNewChat(title: String?): Result<NewChatResponse> = safeApiCall { apiService.createNewChat(NewChatRequest(title)) }

    suspend fun getChatHistory(chatId: String): Result<ChatHistoryResponse> = safeApiCall { apiService.getChatHistory(chatId) }

    suspend fun deleteChat(chatId: String): Result<GenericSuccessResponse> = safeApiCall { apiService.deleteChat(chatId) }

    suspend fun updateChatTitle(chatId: String, newTitle: String): Result<GenericSuccessResponse> = safeApiCall { apiService.updateChatTitle(chatId, TitleUpdateRequest(newTitle)) }

    fun sendMessageAndStream(
        chatId: String,
        prompt: String,
        langCodeForTTS: String?,
        configKeyForTTS: String?
    ): Flow<ChatStreamEvent> = channelFlow {
        val messageRequest = SendMessageRequest(prompt, langCodeForTTS, configKeyForTTS)
        val jsonBody = gson.toJson(messageRequest)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val token = tokenManager.getToken()

        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}chat/$chatId/message")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        val listener = SseListenerRepository(this, gson)
        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    // --- Structured Exam Methods ---

    suspend fun getInitialExamQuestion(): Result<ExamStepResponse> = safeApiCall { apiService.startExam() }

    fun getNextExamStepStream(request: ExamStepRequest): Flow<ExamEvent> = channelFlow {
        val jsonBody = gson.toJson(request)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val token = tokenManager.getToken()

        val httpRequest = Request.Builder()
            .url("${RetrofitClient.BASE_URL}exam/step-stream")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        val listener = ExamSseListener(this, gson)
        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(httpRequest, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    suspend fun analyzeFullExam(transcript: List<TranscriptEntry>): Result<AnalyzeExamResponse> {
        val request = AnalyzeExamRequest(transcript)
        return safeApiCall { apiService.analyzeExam(request) }
    }

    suspend fun getExamHistorySummary(): Result<ExamHistorySummaryResponse> = safeApiCall { apiService.getExamHistory() }

    suspend fun getExamResultDetails(resultId: String): Result<ExamResultResponse> = safeApiCall { apiService.getExamResult(resultId) }
}