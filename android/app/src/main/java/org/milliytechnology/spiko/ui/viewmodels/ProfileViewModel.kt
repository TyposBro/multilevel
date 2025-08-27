package org.milliytechnology.spiko.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.GenericSuccessResponse
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.remote.models.UserProfileResponse
import org.milliytechnology.spiko.data.repositories.AuthRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Data class for UI state, now includes detailed subscription info.
 */
data class UserProfileViewData(
    val id: String,
    val displayName: String,
    val primaryIdentifier: String,
    val registeredDate: String,
    val authProvider: String,
    val subscriptionTier: String?,
    val subscriptionExpiresAt: Date?,
    val isRenewalAllowed: Boolean,
    val fullExamsUsedToday: Int,
    val partPracticesUsedToday: Int,
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

/**
 * Helper extension function to format the API response into rich UI data.
 */
 fun UserProfileResponse.toViewData(): UserProfileViewData {
    val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    val registeredDate = try {
        this.createdAt?.let { dateParser.parse(it) }
    } catch (e: Exception) {
        null
    }

    val displayName = when (this.authProvider?.lowercase()) {
        "google" -> this.firstName ?: this.email ?: "Google User"
        "telegram" -> this.firstName ?: this.username?.let { "@$it" } ?: "Telegram User"
        else -> "User"
    }

    val primaryIdentifier = this.email ?: this.telegramId?.toString() ?: "No identifier"

    val providerName = this.authProvider?.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    } ?: "Unknown"

    // --- Expanded logic to calculate tier, date, and renewal flag ---
    val expiresAtDate: Date? = this.subscriptionExpiresAt?.let {
        try {
            dateParser.parse(it)
        } catch (e: Exception) {
            null
        }
    }

    val now = Date()
    val isExpired = expiresAtDate == null || expiresAtDate.before(now)


    val daysUntilExpiry = if (expiresAtDate != null && !isExpired) {
        TimeUnit.MILLISECONDS.toDays(expiresAtDate.time - now.time)
    } else {
        -1L // Represents expired or no subscription
    }

    // Renewal is allowed if the subscription is active and expires within 7 days.
    val isRenewalAllowed = daysUntilExpiry in 0..7
    val fullExamsUsed = this.dailyUsageFullExamsCount ?: 0
    val partPracticesUsed = this.dailyUsagePartPracticesCount ?: 0

    return UserProfileViewData(
        id = this.id,
        displayName = displayName,
        primaryIdentifier = primaryIdentifier,
        registeredDate = registeredDate?.let { dateFormatter.format(it) } ?: "N/A",
        authProvider = providerName,
        subscriptionTier = this.subscriptionTier,
        subscriptionExpiresAt = if (isExpired) null else expiresAtDate, // Only pass date if not expired
        isRenewalAllowed = isRenewalAllowed,
        fullExamsUsedToday = fullExamsUsed,
        partPracticesUsedToday = partPracticesUsed
    )
}