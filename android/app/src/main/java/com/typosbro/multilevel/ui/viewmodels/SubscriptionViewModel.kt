package com.typosbro.multilevel.ui.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.repositories.PaymentRepository
import com.typosbro.multilevel.data.repositories.SubscriptionRepository
import com.typosbro.multilevel.utils.openUrlInCustomTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val purchaseSuccessMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository // INJECT THE NEW REPOSITORY
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Step 1 of the web payment flow: Create the payment on the backend.
     * This will open a browser/custom tab for the user to pay.
     */
    fun createWebPayment(activity: Activity, provider: String, planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = paymentRepository.createPayment(provider, planId)) {
                is RepositoryResult.Success -> {
                    // Successfully got the payment URL
                    val paymentUrl = result.data.paymentUrl
                    val receiptId = result.data.receiptId

                    // TODO: Save the `receiptId` and `planId` locally (e.g., in a DataStore or
                    // a simple preference) so we know what to verify when the user returns to the app.
                    // Example: paymentPrefManager.savePendingTransaction(receiptId, planId)

                    // Open the URL for the user to pay
                    openUrlInCustomTab(activity, paymentUrl)
                    _uiState.update { it.copy(isLoading = false) }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    /**
     * Step 2 of the web payment flow: Verify the transaction after the user returns to the app.
     */
    fun verifyPendingPurchase(provider: String) {
        viewModelScope.launch {
            // TODO: Retrieve the saved `receiptId` and `planId` from your local storage.
            // val pendingTx = paymentPrefManager.getPendingTransaction() ?: return@launch
            // val receiptId = pendingTx.receiptId
            // val planId = pendingTx.planId

            // For now, we will hardcode for demonstration
            val receiptId = "HARDCODED_RECEIPT_ID_FOR_TESTING"
            val planId = "gold_monthly"

            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = subscriptionRepository.verifyPurchase(provider, receiptId, planId)) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            purchaseSuccessMessage = result.data.message
                        )
                    }
                    // TODO: Clear the pending transaction from local storage.
                    // paymentPrefManager.clearPendingTransaction()
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }


    fun clearMessages() {
        _uiState.update { it.copy(purchaseSuccessMessage = null, error = null) }
    }
}