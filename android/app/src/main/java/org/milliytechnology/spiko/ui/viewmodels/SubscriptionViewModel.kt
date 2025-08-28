package org.milliytechnology.spiko.ui.viewmodels

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.repositories.AuthRepository
import org.milliytechnology.spiko.data.repositories.PaymentRepository
import org.milliytechnology.spiko.data.repositories.SubscriptionRepository
import org.milliytechnology.spiko.features.billing.BillingClientWrapper
import javax.inject.Inject

data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val productDetails: List<ProductDetails> = emptyList(),
    val purchaseSuccessMessage: String? = null,
    val error: String? = null,
    val paymentUrlToLaunch: String? = null,
    val userProfile: UserProfileViewData? = null,
    val pendingPurchasePlanId: String? = null // <-- ADD THIS LINE
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val billingClient: BillingClientWrapper,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // This coroutine now manages the entire loading lifecycle.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            fetchUserProfile()

            billingClient.startConnection()
            billingClient.isReady.filter { it }.first()
            loadProducts() // This will now correctly turn off isLoading at the end.
        }

        // This observer's ONLY job is to update the productDetails list.
        // It no longer touches the `isLoading` flag.
        billingClient.productDetails.onEach { products ->
            _uiState.update { it.copy(productDetails = products) }
        }.launchIn(viewModelScope)

        billingClient.purchases.onEach { purchases ->
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {

                    // --- THE CORE FIX ---
                    // 1. Try to get planId from the purchase object first.
                    var planId = purchase.products.firstOrNull()

                    // 2. If it's null, use our fallback from the UI state.
                    if (planId == null) {
                        planId = _uiState.value.pendingPurchasePlanId
                        Log.w("BillingFix", "planId was missing in Purchase object. Using pending planId from state: $planId")
                    }

                    if (planId != null) {
                        Log.d("BillingTest", "Purchase is PURCHASED and NOT acknowledged. Verifying with backend for planId: $planId")
                        verifyAndAcknowledgePurchase(
                            provider = "google",
                            token = purchase.purchaseToken,
                            planId = planId,
                            purchase = purchase
                        )
                        // 3. Clear the pending ID after using it.
                        _uiState.update { it.copy(pendingPurchasePlanId = null) }
                    } else {
                        Log.e("BillingTest", "FATAL: Purchase is missing product ID and no pending ID was found. Cannot verify.")
                        _uiState.update { it.copy(error = "Purchase verification failed: Missing Product ID.") }
                    }
                } else {
                    Log.d("BillingTest", "Purchase does not meet criteria for verification. Skipping.")
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            when (val result = authRepository.getUserProfile()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(userProfile = result.data.toViewData())
                    }
                }
                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            val productIds = listOf("silver_monthly", "gold_monthly")
            billingClient.queryProductDetails(productIds)
            // Now that everything has been fetched, we can safely turn off the loading indicator.
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun launchGooglePlayPurchase(activity: Activity, productDetails: ProductDetails) {
        // --- FIX: Store the planId before launching the flow ---
        _uiState.update { it.copy(pendingPurchasePlanId = productDetails.productId) }
        billingClient.launchPurchaseFlow(activity, productDetails)
    }

    private fun verifyAndAcknowledgePurchase(
        provider: String,
        token: String,
        planId: String,
        purchase: Purchase
    ) {
        viewModelScope.launch {
            Log.d("BillingTest", "verifyAndAcknowledgePurchase: Sending token ...${token.takeLast(12)} for plan '$planId' to backend.")
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = subscriptionRepository.verifyPurchase(provider, token, planId)) {
                is RepositoryResult.Success -> {
                    Log.d("BillingTest", "Backend verification SUCCESSFUL for token ...${token.takeLast(12)}. Now acknowledging with Google Play.")

                    // Acknowledge the purchase with Google
                    billingClient.acknowledgePurchase(purchase)

                    Log.d("BillingTest", "Google Play acknowledgement call has been made.")
                    _uiState.update {
                        it.copy(isLoading = false, purchaseSuccessMessage = result.data.message)
                    }
                }
                is RepositoryResult.Error -> {
                    Log.e("BillingTest", "Backend verification FAILED for token ...${token.takeLast(12)}. Error: ${result.message}. WILL NOT acknowledge purchase.")
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun createClickPayment(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = paymentRepository.createPayment("click", planId)) {
                is RepositoryResult.Success -> {
                    val paymentUrl = result.data.paymentUrl
                    if (!paymentUrl.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(isLoading = false, paymentUrlToLaunch = paymentUrl)
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, error = "Failed to get payment URL from server.")
                        }
                    }
                }
                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun onPurchaseCompleted() {
        fetchUserProfile()
    }

    fun clearPaymentUrl() {
        _uiState.update { it.copy(paymentUrlToLaunch = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(purchaseSuccessMessage = null, error = null) }
    }

    override fun onCleared() {
        super.onCleared()
//        billingClient.endConnection()
    }
}