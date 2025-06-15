// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ProfileViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// A simple data class for user profile details
data class UserProfile(
    val email: String? = "user@example.com"
    // Add other details like name, subscription_tier, etc.
)

data class ProfileUiState(
    val userProfile: UserProfile = UserProfile(),
    val isDarkTheme: Boolean = false // This would be loaded from DataStore
)


@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenManager: TokenManager
    // We will get the AuthViewModel from the UI layer
) : ViewModel(){

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadUserProfile()
        loadSettings()
    }

    private fun loadUserProfile() {
        // In a real app, you might decode the JWT or fetch from a /me endpoint.
        // For now, we'll just show a placeholder or a simple decoded value.
        // This is a simplified example.
        val email = "user@example.com" // Placeholder
        _uiState.update { it.copy(userProfile = UserProfile(email = email)) }
    }

    private fun loadSettings() {
        // Here, you would load settings from SharedPreferences or Jetpack DataStore.
        // For this example, we just keep it in memory.
        viewModelScope.launch {
            // val isDark = settingsRepository.getThemeFlow().first()
            // _uiState.update { it.copy(isDarkTheme = isDark) }
        }
    }

    fun onThemeChanged(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
        // viewModelScope.launch { settingsRepository.setTheme(isDark) }
    }

    fun logout(authViewModel: AuthViewModel) {
        authViewModel.logout()
    }
}