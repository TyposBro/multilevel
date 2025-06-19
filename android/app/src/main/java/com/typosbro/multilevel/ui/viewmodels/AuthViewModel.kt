// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/AuthViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.data.remote.models.AuthRequest
import com.typosbro.multilevel.data.remote.models.AuthResponse
import com.typosbro.multilevel.data.remote.models.GoogleSignInRequest
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager // No longer needs TokenManager
) : BaseViewModel() {

    fun login(email: String, password: String) {
        launchDataLoad(
            block = { authRepository.login(AuthRequest(email, password)) },
            onSuccess = { authResponse ->
                // Tell the SessionManager to update its state with the new token.
                sessionManager.updateToken(authResponse.token)
            }
        )
    }

    fun register(email: String, password: String) {
        launchDataLoad(
            block = { authRepository.register(AuthRequest(email, password)) },
            onSuccess = { authResponse ->
                // Tell the SessionManager to update its state with the new token.
                sessionManager.updateToken(authResponse.token)
            }
        )
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.logout()
        }
    }

    fun getSessionManager(): SessionManager = sessionManager

    // Social Auth  Methods
    private val _googleSignInState = MutableStateFlow<UiState<AuthResponse>>(UiState.Idle)
    val googleSignInState = _googleSignInState.asStateFlow()

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _googleSignInState.value = UiState.Loading
            val result = authRepository.googleSignIn(GoogleSignInRequest(idToken))
            when (result) {
                is RepositoryResult.Success -> {
                    // SUCCESS! Save the token from YOUR backend.
                    sessionManager.updateToken(result.data.token)
                    _googleSignInState.value = UiState.Success(result.data)
                }

                is RepositoryResult.Error -> {
                    _googleSignInState.value = UiState.Error(result.message)
                }
            }
        }
    }
}

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}