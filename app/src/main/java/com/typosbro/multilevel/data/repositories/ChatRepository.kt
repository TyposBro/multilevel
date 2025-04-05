
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.ChatHistoryResponse
import com.typosbro.multilevel.data.remote.models.ChatListResponse
import com.typosbro.multilevel.data.remote.models.CreateChatRequest
import com.typosbro.multilevel.data.remote.models.CreateChatResponse
import com.typosbro.multilevel.data.remote.models.DeleteChatResponse
import com.typosbro.multilevel.data.remote.models.SendMessageRequest
import com.typosbro.multilevel.data.remote.models.SendMessageResponse
import com.typosbro.multilevel.data.remote.models.UpdateTitleRequest
import com.typosbro.multilevel.data.remote.models.UpdateTitleResponse
import com.typosbro.multilevel.data.repositories.Result
import com.typosbro.multilevel.data.repositories.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Chat Repository ---
class ChatRepository(private val apiService: ApiService) {
    suspend fun getChatList(): com.typosbro.multilevel.data.repositories.Result<ChatListResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.listChats() }
    }

    suspend fun createNewChat(title: String? = null): com.typosbro.multilevel.data.repositories.Result<CreateChatResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.createChat(CreateChatRequest(title)) }
    }

    suspend fun deleteChat(chatId: String): com.typosbro.multilevel.data.repositories.Result<DeleteChatResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.deleteChat(chatId) }
    }

    suspend fun updateChatTitle(chatId: String, newTitle: String): com.typosbro.multilevel.data.repositories.Result<UpdateTitleResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.updateChatTitle(chatId, UpdateTitleRequest(newTitle)) }
    }

    suspend fun getChatHistory(chatId: String): com.typosbro.multilevel.data.repositories.Result<ChatHistoryResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.getChatHistory(chatId) }
    }

    suspend fun sendMessage(chatId: String, prompt: String): Result<SendMessageResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.sendMessage(chatId, SendMessageRequest(prompt)) }
    }
}