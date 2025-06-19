package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.data.remote.models.AuthRequest
import com.typosbro.multilevel.data.remote.models.AuthResponse
import com.typosbro.multilevel.data.remote.models.GoogleSignInRequest
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // --- REMOVED old `isLoading` and `error` flows ---

    // --- NEW: Specific state holder for Email/Password login ---
    private val _loginState = MutableStateFlow<UiState<AuthResponse>>(UiState.Idle)
    val loginState = _loginState.asStateFlow()

    // --- NEW: Specific state holder for Registration ---
    private val _registrationState = MutableStateFlow<UiState<AuthResponse>>(UiState.Idle)
    val registrationState = _registrationState.asStateFlow()

    // --- State holder for Google Sign-In ---
    private val _googleSignInState = MutableStateFlow<UiState<AuthResponse>>(UiState.Idle)
    val googleSignInState = _googleSignInState.asStateFlow()


    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = UiState.Loading
            val result = authRepository.login(AuthRequest(email.trim(), password))
            when (result) {
                is RepositoryResult.Success -> {
                    sessionManager.updateToken(result.data.token)
                    _loginState.value = UiState.Success(result.data)
                }

                is RepositoryResult.Error -> {
                    _loginState.value = UiState.Error(result.message)
                }
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _registrationState.value = UiState.Loading
            val result = authRepository.register(AuthRequest(email.trim(), password))
            when (result) {
                is RepositoryResult.Success -> {
                    sessionManager.updateToken(result.data.token)
                    _registrationState.value = UiState.Success(result.data)
                }

                is RepositoryResult.Error -> {
                    _registrationState.value = UiState.Error(result.message)
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _googleSignInState.value = UiState.Loading
            val result = authRepository.googleSignIn(GoogleSignInRequest(idToken))
            when (result) {
                is RepositoryResult.Success -> {
                    sessionManager.updateToken(result.data.token)
                    _googleSignInState.value = UiState.Success(result.data)
                }

                is RepositoryResult.Error -> {
                    _googleSignInState.value = UiState.Error(result.message)
                }
            }
        }
    }

    /**
     * Allows the UI to report a client-side error from the Google Sign-In flow,
     * such as the user canceling the dialog or a network issue on the device.
     */
    fun setGoogleSignInError(message: String) {
        _googleSignInState.value = UiState.Error(message)
    }

    /**
     * Resets all authentication states to Idle. This is useful for clearing errors
     * when the user navigates away or when a new auth action is initiated.
     */
    fun resetAllStates() {
        _loginState.value = UiState.Idle
        _registrationState.value = UiState.Idle
        _googleSignInState.value = UiState.Idle
    }

    // This method is still useful for your AppNavigation to get a handle on the session manager
    fun getSessionManager(): SessionManager {
        return sessionManager
    }

    /**
     * Logs the user out by clearing the token from the SessionManager.
     * The UI navigation will react automatically to the token becoming null.
     */
    suspend fun logout() {
        // Run on the IO dispatcher as it involves SharedPreferences I/O
        withContext(Dispatchers.IO) {
            sessionManager.logout()
        }
    }
}