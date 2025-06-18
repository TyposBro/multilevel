// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/AuthViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.remote.models.AuthRequest
import com.typosbro.multilevel.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager
) : BaseViewModel() {

    // --- REMOVED ---
    // private val _authenticationSuccessful = MutableStateFlow(false)
    // val authenticationSuccessful = _authenticationSuccessful.asStateFlow()

    fun login(email: String, password: String) {
        launchDataLoad(
            block = { authRepository.login(AuthRequest(email, password)) },
            onSuccess = { authResponse ->
                // The ONLY responsibility is to save the token.
                // Navigation will be handled by observing the token's state.
                tokenManager.saveToken(authResponse.token)
            }
        )
    }

    fun register(email: String, password: String) {
        launchDataLoad(
            block = { authRepository.register(AuthRequest(email, password)) },
            onSuccess = { authResponse ->
                tokenManager.saveToken(authResponse.token)
            }
        )
    }

    // --- REMOVED ---
    // fun resetAuthStatus() { ... }

    fun logout() {
        viewModelScope.launch {
            sessionManager.logout()
        }
    }

    fun getSessionManager(): SessionManager = sessionManager
}