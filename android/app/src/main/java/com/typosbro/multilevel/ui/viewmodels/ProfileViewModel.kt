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
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

// We can enhance the user profile with a formatted date
data class UserProfileViewData(
    val id: String,
    val email: String,
    val registeredDate: String
)

// The UI State for the Profile Screen
data class ProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfile: UserProfileViewData? = null
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
                            userProfile = result.data.toViewData() // Map to a display-friendly model
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

    // --- REMOVED `logout` and `onThemeChanged` for simplicity ---
    // The UI will handle these concerns directly.
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
    return UserProfileViewData(
        id = this.id,
        email = this.email,
        registeredDate = date?.let { formatter.format(it) } ?: "N/A"
    )
}