package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.RetrofitClient
import com.typosbro.multilevel.data.repositories.AuthRepository
import com.typosbro.multilevel.data.repositories.ChatRepository

// Generic Factory using CreationExtras (modern way)
object AppViewModelProvider {
    val Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as Application
            val savedStateHandle = extras.createSavedStateHandle() // Get SavedStateHandle

            // Lazily initialize dependencies
            val apiService by lazy { RetrofitClient.create(application) }
            val tokenManager by lazy { TokenManager(application) }
            val authRepository by lazy { AuthRepository(apiService) }
            val chatRepository by lazy { ChatRepository(apiService) }


            return when {
                modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                    AuthViewModel(authRepository, tokenManager) as T
                modelClass.isAssignableFrom(ChatListViewModel::class.java) ->
                    ChatListViewModel(chatRepository) as T
                modelClass.isAssignableFrom(ChatDetailViewModel::class.java) ->
                    ChatDetailViewModel(application, savedStateHandle, chatRepository) as T // Pass dependencies

                // Add other ViewModels here
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}