package com.typosbro.multilevel.data.remote.models

import com.google.gson.annotations.SerializedName

// --- Models for Chat List ---

data class ChatListResponse(
    @SerializedName("chats") val chats: List<ChatSummary>
)

data class ChatSummary(
    @SerializedName("_id") val _id: String,
    @SerializedName("title") val title: String,
    @SerializedName("createdAt") val createdAt: String, // Or use a Date/Long and a TypeAdapter
    @SerializedName("updatedAt") val updatedAt: String
)

// --- Models for Creating a New Chat ---

data class NewChatRequest(
    @SerializedName("title") val title: String?
)

data class NewChatResponse(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("title") val title: String,
    @SerializedName("createdAt") val createdAt: String
)

// --- Models for Getting Chat History ---

data class ChatHistoryResponse(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("title") val title: String,
    @SerializedName("history") val history: List<ChatMessageApi>,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class ChatMessageApi(
    @SerializedName("role") val role: String, // "user" or "model"
    @SerializedName("parts") val parts: List<MessagePart>
)

data class MessagePart(
    @SerializedName("text") val text: String
)

// --- Models for Sending a Message (in freestyle chat) ---

data class SendMessageRequest(
    val prompt: String,
    val lang_code: String?,
    val config_key: String?
)


// --- Models for Updating Title ---

data class TitleUpdateRequest(
    @SerializedName("title") val title: String
)

// --- A Generic Response for simple success cases (like delete/update) ---

data class GenericSuccessResponse(
    @SerializedName("message") val message: String
)