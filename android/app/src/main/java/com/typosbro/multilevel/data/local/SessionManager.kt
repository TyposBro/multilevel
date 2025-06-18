package com.typosbro.multilevel.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the global authentication session state.
 * This class is a Singleton, so there's only one instance in the entire app.
 * It allows us to broadcast a logout event from anywhere (like a network interceptor)
 * and have the UI layer (like AppNavigation) react to it.
 */
@Singleton
class SessionManager @Inject constructor(private val tokenManager: TokenManager) {

    // A SharedFlow is used to broadcast events to all collectors.
    // It doesn't hold a value like StateFlow, it just emits events.
    private val _logoutEvents = MutableSharedFlow<Unit>()
    val logoutEvents = _logoutEvents.asSharedFlow()

    /**
     * Call this function to perform a logout.
     * It clears the token and sends a signal to all listeners that a logout has occurred.
     */
    suspend fun logout() {
        tokenManager.clearToken()
        _logoutEvents.emit(Unit) // Emit a signal on the flow
    }

    fun getToken(): String? {
        return tokenManager.getToken()
    }
}