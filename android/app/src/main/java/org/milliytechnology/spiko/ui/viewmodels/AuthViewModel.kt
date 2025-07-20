package org.milliytechnology.spiko.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.milliytechnology.spiko.data.local.SessionManager
import org.milliytechnology.spiko.data.remote.models.AuthResponse
import org.milliytechnology.spiko.data.remote.models.GoogleSignInRequest
import org.milliytechnology.spiko.data.remote.models.OneTimeTokenRequest
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.repositories.AuthRepository
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // --- REMOVED: _loginState and _registrationState ---

    // --- State holder for Google Sign-In ---
    private val _googleSignInState = MutableStateFlow<UiState<AuthResponse>>(UiState.Idle)
    val googleSignInState = _googleSignInState.asStateFlow()

    // --- NEW: State holder for the Telegram Deep Link verification process ---
    private val _deepLinkVerifyState = MutableStateFlow<UiState<AuthResponse>>(UiState.Idle)
    val deepLinkVerifyState = _deepLinkVerifyState.asStateFlow()


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
     * Verifies the one-time token received from the Telegram deep link.
     * This is called from MainActivity when a deep link is detected.
     */
    fun verifyOneTimeToken(token: String) {
        // Prevent re-triggering if already loading/successful
        if (_deepLinkVerifyState.value is UiState.Loading || _deepLinkVerifyState.value is UiState.Success) return

        viewModelScope.launch {
            _deepLinkVerifyState.value = UiState.Loading
            val result = authRepository.verifyTelegramToken(OneTimeTokenRequest(token))
            when (result) {
                is RepositoryResult.Success -> {
                    sessionManager.updateToken(result.data.token)
                    _deepLinkVerifyState.value = UiState.Success(result.data)
                }

                is RepositoryResult.Error -> {
                    _deepLinkVerifyState.value = UiState.Error(result.message)
                }
            }
        }
    }


    /**
     * Allows the UI to report a client-side error from the Google Sign-In flow,
     * such as the user canceling the dialog.
     */
    fun setGoogleSignInError(message: String) {
        _googleSignInState.value = UiState.Error(message)
    }

    /**
     * Resets all authentication states to Idle. This is useful for clearing errors.
     */
    fun resetAllStates() {
        _googleSignInState.value = UiState.Idle
        _deepLinkVerifyState.value = UiState.Idle
    }

    // This method is still useful for your AppNavigation to get a handle on the session manager
    fun getSessionManager(): SessionManager {
        return sessionManager
    }

    /**
     * Logs the user out by clearing the token from the SessionManager.
     */
    suspend fun logout() {
        withContext(Dispatchers.IO) {
            sessionManager.logout()
            resetAllStates() // Also reset UI states on logout
        }
    }
}