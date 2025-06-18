// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/AuthViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.data.remote.models.AuthRequest
import com.typosbro.multilevel.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
}