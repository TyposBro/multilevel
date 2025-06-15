// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/ApiService.kt
package com.typosbro.multilevel.data.remote

import com.typosbro.multilevel.data.remote.models.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // --- Auth Endpoints ---
    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    // --- Freestyle Chat Endpoints ---
    @GET("chat/")
    suspend fun getChatList(): Response<ChatListResponse>

    @POST("chat/")
    suspend fun createNewChat(@Body request: NewChatRequest): Response<NewChatResponse>

    @GET("chat/{chatId}/history")
    suspend fun getChatHistory(@Path("chatId") chatId: String): Response<ChatHistoryResponse>

    // --- NEW: Add the streaming endpoint for freestyle chat ---
    @POST("chat/{chatId}/message")
    @Streaming // Important: Tells Retrofit to handle this as a streaming response
    fun sendMessageAndStream(
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest
    ): Call<ResponseBody> // Return a Call<ResponseBody> for SSE

    @DELETE("chat/{chatId}")
    suspend fun deleteChat(@Path("chatId") chatId: String): Response<GenericSuccessResponse>

    @PUT("chat/{chatId}/title")
    suspend fun updateChatTitle(
        @Path("chatId") chatId: String,
        @Body request: TitleUpdateRequest
    ): Response<GenericSuccessResponse>

    // --- Structured Exam Endpoints ---
    @POST("exam/start")
    suspend fun startExam(): Response<ExamStepResponse>

    // --- NEW: Add the streaming endpoint for exams ---
    @POST("exam/step-stream")
    @Streaming // Important: Tells Retrofit to handle this as a streaming response
    fun getNextExamStepStream(@Body request: ExamStepRequest): Call<ResponseBody>

    @POST("exam/analyze")
    suspend fun analyzeExam(@Body request: AnalyzeExamRequest): Response<AnalyzeExamResponse>

    @GET("exam/history")
    suspend fun getExamHistory(): Response<ExamHistorySummaryResponse>

    @GET("exam/result/{resultId}")
    suspend fun getExamResult(@Path("resultId") resultId: String): Response<ExamResultResponse>
}