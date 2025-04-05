package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.ChatSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.typosbro.multilevel.data.repositories.ChatRepository
import com.typosbro.multilevel.data.repositories.Result


class ChatListViewModel(private val chatRepository: ChatRepository) : BaseViewModel() {
    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats = _chats.asStateFlow()

    // State to hold the ID of a newly created chat for navigation
    private val _navigateToChatId = MutableStateFlow<String?>(null)
    val navigateToChatId = _navigateToChatId.asStateFlow()


    init {
        loadChats()
    }

    fun loadChats() {
        launchDataLoad(
            block = { chatRepository.getChatList() },
            onSuccess = { response -> _chats.value = response.chats }
        )
    }

    fun createNewChat(title: String? = null) {
        launchDataLoad(
            block = { chatRepository.createNewChat(title) },
            onSuccess = { response ->
                // Optionally refresh the list immediately or rely on user pull-to-refresh
                loadChats() // Refresh the list to include the new chat
                _navigateToChatId.value = response.chatId // Signal navigation
            }
        )
    }

    fun deleteChat(chatId: String) {
        launchAction(
            block = { chatRepository.deleteChat(chatId) },
            onComplete = {
                // Remove the chat from the local list optimistically or reload
                _chats.update { currentList -> currentList.filterNot { it._id == chatId } }
                // Optionally add undo functionality here
            }
        )
    }

    fun renameChat(chatId: String, newTitle: String) {
        launchAction(
            block = { chatRepository.updateChatTitle(chatId, newTitle) },
            onComplete = {
                // Update the title in the local list or reload
                _chats.update { currentList ->
                    currentList.map { if (it._id == chatId) it.copy(title = newTitle) else it }
                }
            }
        )
    }

    // Call this after navigation has occurred
    fun consumedNavigation() {
        _navigateToChatId.value = null
    }
}