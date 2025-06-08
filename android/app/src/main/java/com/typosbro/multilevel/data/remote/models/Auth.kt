package com.typosbro.multilevel.data.remote.models

// --- Auth ---
data class AuthRequest(val email: String, val password: String)
data class AuthResponse(val _id: String, val email: String, val token: String)

// --- Chat ---
// Matches the backend 'Message' schema part inside 'Chat' history
data class ApiMessagePart(val text: String)
data class ApiMessage(val role: String, val parts: List<ApiMessagePart>)

// Summary for Chat List
data class ChatSummary(val _id: String, val title: String, val createdAt: String, val updatedAt: String)
data class ChatListResponse(val chats: List<ChatSummary>)

// Full Chat History Response
data class ChatHistoryResponse(
    val chatId: String,
    val title: String,
    val history: List<ApiMessage>, // Use the ApiMessage structure
    val createdAt: String,
    val updatedAt: String
)

// Request to send a message
data class SendMessageRequest(
    val prompt: String,
    val lang_code: String? = null,
    val config_key: String? = null
)

// Response when sending a message
data class SendMessageResponse(val message: String, val chatId: String)

// Response when creating a new chat
data class CreateChatResponse(val message: String, val chatId: String, val title: String, val createdAt: String)
data class CreateChatRequest(val title: String?) // Optional title

// Response when deleting a chat
data class DeleteChatResponse(val message: String, val chatId: String)

// Request/Response for updating title (Response might include updated chat info)
data class UpdateTitleRequest(val title: String)
data class UpdateTitleResponse(val message: String, val chatId: String, val title: String, val updatedAt: String)

// Generic error response (optional, depends on your backend error structure)
data class ErrorResponse(val message: String)