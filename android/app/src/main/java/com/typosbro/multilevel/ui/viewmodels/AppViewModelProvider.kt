// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/AppViewModelProvider.kt
package com.typosbro.multilevel.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.local.WordDatabase
import com.typosbro.multilevel.data.remote.RetrofitClient // Keep this
import com.typosbro.multilevel.data.repositories.AuthRepository
import com.typosbro.multilevel.data.repositories.ChatRepository
import kotlinx.coroutines.runBlocking // For the simple tokenProvider lambda

// Generic Factory using CreationExtras (modern way)
object AppViewModelProvider {
    val Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as Application
            val savedStateHandle = extras.createSavedStateHandle() // Get SavedStateHandle

            // Lazily initialize dependencies
            val tokenManager by lazy { TokenManager(application) }

            // Get OkHttpClient instance using the method from RetrofitClient
            val okHttpClient by lazy { RetrofitClient.getOkHttpClient(application.applicationContext) }

            // ApiService (used by AuthRepository.kt and potentially non-SSE parts of ChatRepository if you split them)
            val apiService by lazy { RetrofitClient.create(application.applicationContext) }


            val authRepository by lazy { AuthRepository(apiService) }

            // --- Updated ChatRepository instantiation ---
            val chatRepository by lazy {
                ChatRepository(
                    apiService = apiService, // Still pass ApiService for non-streaming methods
                    okHttpClient = okHttpClient, // Pass the OkHttpClient instance
                    tokenProvider = {
                        // This lambda will be called by ChatRepository when it needs a token
                        // runBlocking is used here for simplicity within this factory.
                        // For a more advanced setup, you might inject a flow or a suspend function directly.
                        runBlocking { tokenManager.getToken() }
                    }
                )
            }
            val wordDao by lazy { WordDatabase.getDatabase(application).wordDao() }
            val authViewModelInstance by lazy { AuthViewModel(authRepository, tokenManager) }

            return when {
                modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                    authViewModelInstance as T // Return the shared instance
                modelClass.isAssignableFrom(ChatListViewModel::class.java) ->
                    ChatListViewModel(chatRepository) as T
                modelClass.isAssignableFrom(ChatDetailViewModel::class.java) ->
                    ChatDetailViewModel(application, savedStateHandle, chatRepository) as T
                modelClass.isAssignableFrom(ExamViewModel::class.java) ->
                    ExamViewModel(application, chatRepository) as T
                modelClass.isAssignableFrom(WordBankViewModel::class.java) ->
                    WordBankViewModel(wordDao) as T
                modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                    ProfileViewModel(tokenManager, authViewModelInstance) as T

                modelClass.isAssignableFrom(ProgressViewModel::class.java) ->
                    ProgressViewModel(chatRepository) as T
                // Add other ViewModels here
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}