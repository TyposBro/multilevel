// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/ChatRepository.kt
package com.typosbro.multilevel.data.repositories

import com.google.gson.Gson
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.RetrofitClient
import com.typosbro.multilevel.data.remote.models.*
import com.typosbro.multilevel.data.remote.models.safeApiCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ApiService,
    @Named("SseOkHttpClient") private val okHttpClient: OkHttpClient, // Hilt will provide the correct named instance
    private val tokenManager: TokenManager
) {
    private val gson = Gson()

    // --- Freestyle Chat Methods ---

    suspend fun getChatList(): RepositoryResult<ChatListResponse> =
        safeApiCall { apiService.getChatList() }

    suspend fun createNewChat(title: String?): RepositoryResult<NewChatResponse> =
        safeApiCall { apiService.createNewChat(NewChatRequest(title)) }

    suspend fun getChatHistory(chatId: String): RepositoryResult<ChatHistoryResponse> =
        safeApiCall { apiService.getChatHistory(chatId) }

    suspend fun deleteChat(chatId: String): RepositoryResult<GenericSuccessResponse> =
        safeApiCall { apiService.deleteChat(chatId) }

    suspend fun updateChatTitle(chatId: String, newTitle: String): RepositoryResult<GenericSuccessResponse> =
        safeApiCall { apiService.updateChatTitle(chatId, TitleUpdateRequest(newTitle)) }

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

    suspend fun getInitialExamQuestion(): RepositoryResult<ExamStepResponse> =
        safeApiCall { apiService.startExam() }

    suspend fun getNextExamStep(request: ExamStepRequest): RepositoryResult<ExamStepResponse> {
        return safeApiCall { apiService.getNextExamStep(request) }
    }

    suspend fun analyzeFullExam(transcript: List<TranscriptEntry>): RepositoryResult<AnalyzeExamResponse> {
        val request = AnalyzeExamRequest(transcript)
        return safeApiCall { apiService.analyzeExam(request) }
    }

    suspend fun getExamHistorySummary(): RepositoryResult<ExamHistorySummaryResponse> =
        safeApiCall { apiService.getExamHistory() }

    suspend fun getExamResultDetails(resultId: String): RepositoryResult<ExamResultResponse> =
        safeApiCall { apiService.getExamResult(resultId) }
}