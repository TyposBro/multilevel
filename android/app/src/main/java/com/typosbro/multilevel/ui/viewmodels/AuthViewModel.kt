package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.remote.models.AuthRequest
import com.typosbro.multilevel.data.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.typosbro.multilevel.data.repositories.Result

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : BaseViewModel() {

    // Using simple state for auth success signal
    private val _authenticationSuccessful = MutableStateFlow(false)
    val authenticationSuccessful = _authenticationSuccessful.asStateFlow()

    fun login(email: String, password: String) {
        launchDataLoad(
            block = { authRepository.login(AuthRequest(email, password)) },
            onSuccess = { authResponse ->
                tokenManager.saveToken(authResponse.token)
                _authenticationSuccessful.value = true // Signal success
            }
        )
    }

    fun register(email: String, password: String) {
        launchDataLoad(
            block = { authRepository.register(AuthRequest(email, password)) },
            onSuccess = { authResponse ->
                tokenManager.saveToken(authResponse.token)
                _authenticationSuccessful.value = true // Signal success
            }
        )
    }

    // Reset the success signal when navigating away
    fun resetAuthStatus() {
        _authenticationSuccessful.value = false
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearToken()
            // Optionally add API call to invalidate token on backend
            _authenticationSuccessful.value = false // Ensure state is reset
        }
    }
}