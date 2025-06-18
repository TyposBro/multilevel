// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/ProfileViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.UserProfileResponse
import com.typosbro.multilevel.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Updated UI State to handle loading, errors, and the actual profile data
data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val userProfile: UserProfileResponse = UserProfileResponse("", "Loading...", ""),
    val isDarkTheme: Boolean = false // Placeholder for theme logic
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository // Inject AuthRepository instead of TokenManager
) : ViewModel(){

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // This function will now make a real network call
        fetchUserProfile()
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.getUserProfile()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, userProfile = result.data, error = null)
                    }
                }
                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun onThemeChanged(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
        // In a real app, you would save this to DataStore
    }

    // The logout logic remains the same, delegating to the shared AuthViewModel
    fun logout(authViewModel: AuthViewModel) {
        authViewModel.logout()
    }
}