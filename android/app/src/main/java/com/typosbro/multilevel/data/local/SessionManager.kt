// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/SessionManager.kt
package com.typosbro.multilevel.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(private val tokenManager: TokenManager) {

    // This StateFlow is the new single source of truth for the auth token.
    private val _tokenFlow = MutableStateFlow<String?>(null)
    val tokenFlow = _tokenFlow.asStateFlow()

    private val _logoutEvents = MutableSharedFlow<Unit>()
    val logoutEvents = _logoutEvents.asSharedFlow()

    init {
        // When the app starts, load the token from storage into our StateFlow.
        _tokenFlow.value = tokenManager.getToken()
    }

    /**
     * Call this after a successful login to update the session state.
     */
    fun updateToken(newToken: String) {
        tokenManager.saveToken(newToken)
        _tokenFlow.value = newToken
    }

    /**
     * Call this to log out. It clears the token from storage and updates the session state.
     */
    suspend fun logout() {
        tokenManager.clearToken()
        _tokenFlow.value = null
        // We can still emit this event for any explicit side-effects if needed,
        // but the tokenFlow becoming null will be the primary trigger for navigation.
        _logoutEvents.emit(Unit)
    }
}