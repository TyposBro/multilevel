package com.typosbro.multilevel.data.remote

import com.typosbro.multilevel.data.remote.models.*
import retrofit2.Response // Use Retrofit Response for better error handling
import retrofit2.http.*

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

    @POST("chat/{chatId}/message") // Send message to a specific chat
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>
}