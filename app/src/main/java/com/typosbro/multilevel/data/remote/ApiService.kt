package com.typosbro.multilevel.data.remote

import com.typosbro.multilevel.data.remote.models.*
import okhttp3.ResponseBody
import retrofit2.Response // Use Retrofit Response for better error handling
import retrofit2.http.*


// Define the new Response Data Class
data class SendMessageApiResponse(
    val message: String,
    val chatId: String,
    val audioContent: String?, // Nullable Base64 audio string
    val ttsError: String?     // Nullable TTS error
)

interface ApiService {

    // --- Authentication ---
    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    // --- Chat Management ---
    @POST("chat") // Create new chat
    suspend fun createChat(@Body request: CreateChatRequest? = null): Response<CreateChatResponse> // Optional body

    @GET("chat") // List user's chats
    suspend fun listChats(): Response<ChatListResponse>

    @DELETE("chat/{chatId}") // Delete a chat
    suspend fun deleteChat(@Path("chatId") chatId: String): Response<DeleteChatResponse>

    @PUT("chat/{chatId}/title") // Update chat title
    suspend fun updateChatTitle(
        @Path("chatId") chatId: String,
        @Body request: UpdateTitleRequest
    ): Response<UpdateTitleResponse>

    // --- Chat Interaction ---
    @GET("chat/{chatId}/history") // Get history for a specific chat
    suspend fun getChatHistory(@Path("chatId") chatId: String): Response<ChatHistoryResponse>

    @POST("chat/{chatId}/message")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest
    ): Response<SendMessageApiResponse>
}