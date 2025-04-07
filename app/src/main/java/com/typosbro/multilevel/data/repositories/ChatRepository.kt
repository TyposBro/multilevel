
package com.typosbro.multilevel.data.repositories

import com.google.gson.Gson
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.SendMessageApiResponse
import com.typosbro.multilevel.data.remote.models.ChatHistoryResponse
import com.typosbro.multilevel.data.remote.models.ChatListResponse
import com.typosbro.multilevel.data.remote.models.CreateChatRequest
import com.typosbro.multilevel.data.remote.models.CreateChatResponse
import com.typosbro.multilevel.data.remote.models.DeleteChatResponse
import com.typosbro.multilevel.data.remote.models.SendMessageRequest
import com.typosbro.multilevel.data.remote.models.UpdateTitleRequest
import com.typosbro.multilevel.data.remote.models.UpdateTitleResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class SendMessageResult {
    // Success cases
    data class AudioSuccess(val audioBytes: ByteArray) : SendMessageResult() // Audio received
    data class TextOnlySuccess(val text: String, val ttsError: String?) : SendMessageResult() // JSON fallback

    // Failure case
    data class Error(val message: String, val code: Int? = null) : SendMessageResult()

    // Need equals/hashCode for ByteArray comparison if used in Sets/Maps etc.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return when(this) {
            is AudioSuccess -> other is AudioSuccess && audioBytes.contentEquals(other.audioBytes)
            is TextOnlySuccess -> other is TextOnlySuccess && text == other.text && ttsError == other.ttsError
            is Error -> other is Error && message == other.message && code == other.code
        }
    }
    override fun hashCode(): Int {
        return when(this) {
            is AudioSuccess -> audioBytes.contentHashCode()
            is TextOnlySuccess -> 31 * text.hashCode() + (ttsError?.hashCode() ?: 0)
            is Error -> 31 * message.hashCode() + (code ?: 0)
        }
    }
}

// --- Chat Repository ---
class ChatRepository(private val apiService: ApiService) {
    suspend fun getChatList(): Result<ChatListResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.listChats() }
    }

    suspend fun createNewChat(title: String? = null): Result<CreateChatResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.createChat(CreateChatRequest(title)) }
    }

    suspend fun deleteChat(chatId: String): Result<DeleteChatResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.deleteChat(chatId) }
    }

    suspend fun updateChatTitle(chatId: String, newTitle: String): Result<UpdateTitleResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.updateChatTitle(chatId, UpdateTitleRequest(newTitle)) }
    }

    suspend fun getChatHistory(chatId: String): Result<ChatHistoryResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.getChatHistory(chatId) }
    }

    suspend fun sendMessage(chatId: String, prompt: String): Result<SendMessageApiResponse> = withContext(Dispatchers.IO) {
        // Use the standard safeApiCall helper
        safeApiCall { apiService.sendMessage(chatId, SendMessageRequest(prompt)) }
    }
}