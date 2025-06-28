package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.GenericSuccessResponse
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.UserProfileResponse
import com.typosbro.multilevel.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class UserProfileViewData(
    val id: String,
    val displayName: String,
    val primaryIdentifier: String,
    val registeredDate: String,
    val authProvider: String,
)

data class ProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfile: UserProfileViewData? = null,
    val deleteState: UiState<GenericSuccessResponse> = UiState.Idle
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchUserProfile()
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.getUserProfile()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userProfile = result.data.toViewData()
                        )
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

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(deleteState = UiState.Loading) }
            val result = authRepository.deleteUserProfile()
            when (result) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(deleteState = UiState.Success(result.data)) }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(deleteState = UiState.Error(result.message)) }
                }
            }
        }
    }

    fun resetDeleteState() {
        _uiState.update { it.copy(deleteState = UiState.Idle) }
    }
}

// Helper extension function to format the data for the UI
private fun UserProfileResponse.toViewData(): UserProfileViewData {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    val date = try {
        parser.parse(this.createdAt)
    } catch (e: Exception) {
        null
    }

    // --- THIS IS THE CORRECTED LOGIC ---
    // We use the safe call operator (?.) to prevent a crash if authProvider is null.
    // The Elvis operator (?:) provides a default value ("unknown") in that case.
    val displayName = when (this.authProvider?.lowercase()) {
        "google" -> this.firstName ?: this.email ?: "Google User"
        "telegram" -> this.firstName ?: this.username?.let { "@$it" } ?: "Telegram User"
        else -> "User" // Default for unknown or null providers
    }

    val primaryIdentifier = this.email ?: this.telegramId?.toString() ?: "No identifier"

    val providerName = this.authProvider?.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    } ?: "Unknown"
    // --- END OF CORRECTION ---

    return UserProfileViewData(
        id = this.id,
        displayName = displayName,
        primaryIdentifier = primaryIdentifier,
        registeredDate = date?.let { formatter.format(it) } ?: "N/A",
        authProvider = providerName
    )
}