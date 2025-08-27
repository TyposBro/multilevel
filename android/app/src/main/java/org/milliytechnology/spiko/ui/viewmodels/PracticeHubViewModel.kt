package org.milliytechnology.spiko.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.repositories.AuthRepository
import org.milliytechnology.spiko.ui.screens.practice.PracticePart
import javax.inject.Inject

data class PracticeHubUiState(
    val isLoading: Boolean = false,
    val showPaywallDialog: Boolean = false,
    val paywallMessage: String = ""
)

/**
 * One-shot events for navigation, ensuring navigation is triggered only once.
 */
sealed class PracticeHubEvent {
    data class NavigateToExam(val practicePart: PracticePart) : PracticeHubEvent()
    object ShowErrorToast : PracticeHubEvent() // Generic error event
}

@HiltViewModel
class PracticeHubViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeHubUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PracticeHubEvent>()
    val events = _events.asSharedFlow()

    /**
     * This is the entry point function called by the UI when a user taps a practice part.
     * It performs the pre-flight check.
     */
    fun onPartSelected(practicePart: PracticePart) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Always fetch the latest user profile to get the most current usage stats.
            when (val result = authRepository.getUserProfile()) {
                is RepositoryResult.Success -> {
                    val userProfile = result.data.toViewData()

                    // Define free tier limits here for clarity.
                    val maxFreeFullExams = 1
                    val maxFreePartPractices = 3

                    var canProceed = false
                    var message = ""

                    // Paid users can always proceed.
                    if (userProfile.subscriptionTier?.lowercase() != "free") {
                        canProceed = true
                    } else {
                        // Logic for free users.
                        when (practicePart) {
                            PracticePart.FULL -> {
                                if (userProfile.fullExamsUsedToday < maxFreeFullExams) {
                                    canProceed = true
                                } else {
                                    message = "You have used your free full mock exam for today. Upgrade to premium for unlimited practice."
                                }
                            }
                            else -> { // Any other selection is a "part practice"
                                if (userProfile.partPracticesUsedToday < maxFreePartPractices) {
                                    canProceed = true
                                } else {
                                    message = "You have used all ${maxFreePartPractices} of your free part practices for today. Upgrade for unlimited access."
                                }
                            }
                        }
                    }

                    if (canProceed) {
                        // If allowed, emit a navigation event.
                        _events.emit(PracticeHubEvent.NavigateToExam(practicePart))
                    } else {
                        // If not allowed, update the state to show the paywall dialog.
                        _uiState.update { it.copy(showPaywallDialog = true, paywallMessage = message) }
                    }
                }
                is RepositoryResult.Error -> {
                    // If fetching the profile fails, emit an error event.
                    _events.emit(PracticeHubEvent.ShowErrorToast)
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Called by the UI to dismiss the paywall dialog.
     */
    fun dismissPaywallDialog() {
        _uiState.update { it.copy(showPaywallDialog = false, paywallMessage = "") }
    }
}