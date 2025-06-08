package com.typosbro.multilevel.data.remote

import com.typosbro.multilevel.data.remote.models.*
import retrofit2.Response
import retrofit2.http.*

// Define Preprocessed data structure based on your API response
data class PreprocessedData(
    val phonemes: String?,
    val input_ids: List<List<Int>>?, // This is what we need for TTS
    val graphemes: String?,
    val lang_code_used: String?,
    val config_key_used: String?
)

// Update SendMessageApiResponse
data class SendMessageApiResponse(
    val message: String, // The text message from the bot
    val chatId: String,
    @Deprecated("audioContent is deprecated. TTS is now handled client-side using preprocessed.input_ids.")
    val audioContent: String?, // Keep for backend compatibility if it's still sent, but mark as deprecated
    val ttsError: String?,     // Backend might still send this if it had an issue *preparing* data
    val preprocessed: PreprocessedData? // New field containing input_ids
)

interface ApiService {

    // --- Authentication ---
    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    // --- Chat Management ---
    @POST("chat")
    suspend fun createChat(@Body request: CreateChatRequest? = null): Response<CreateChatResponse>

    @GET("chat")
    suspend fun listChats(): Response<ChatListResponse>

    @DELETE("chat/{chatId}")
    suspend fun deleteChat(@Path("chatId") chatId: String): Response<DeleteChatResponse>

    @PUT("chat/{chatId}/title")
    suspend fun updateChatTitle(
        @Path("chatId") chatId: String,
        @Body request: UpdateTitleRequest
    ): Response<UpdateTitleResponse>

    // --- Chat Interaction ---
    @GET("chat/{chatId}/history")
    suspend fun getChatHistory(@Path("chatId") chatId: String): Response<ChatHistoryResponse>

    @POST("chat/{chatId}/message")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest // SendMessageRequest contains { "prompt": "..." }
    ): Response<SendMessageApiResponse> // Ensure this uses the updated SendMessageApiResponse
}