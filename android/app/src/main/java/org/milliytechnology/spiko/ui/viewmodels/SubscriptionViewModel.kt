// android/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/SubscriptionViewModel.kt

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.repositories.PaymentRepository
import org.milliytechnology.spiko.data.repositories.SubscriptionRepository
import org.milliytechnology.spiko.features.billing.BillingClientWrapper
import org.milliytechnology.spiko.utils.openUrlInCustomTab
import javax.inject.Inject

data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val productDetails: List<ProductDetails> = emptyList(),
    val purchaseSuccessMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val billingClient: BillingClientWrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        billingClient.startConnection()

        // Observe product details from the BillingClientWrapper
        billingClient.productDetails.onEach { products ->
            _uiState.update { it.copy(productDetails = products) }
        }.launchIn(viewModelScope)

        // Observe new purchases from the BillingClientWrapper
        billingClient.purchases.onEach { purchases ->
            purchases.forEach { purchase ->
                // Process only new, unacknowledged purchases
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                    val planId = purchase.products.firstOrNull()
                    if (planId != null) {
                        verifyAndAcknowledgePurchase(
                            provider = "google",
                            token = purchase.purchaseToken,
                            planId = planId,
                            purchase = purchase
                        )
                    } else {
                        Log.e("SubscriptionVM", "Purchase is missing product ID. Cannot verify.")
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Called by the UI to fetch product details from the Google Play Store.
     */
    fun loadProducts() {
        viewModelScope.launch {
            // Your actual product IDs from the Google Play Console
            val productIds = listOf("silver_monthly", "gold_monthly")
            billingClient.queryProductDetails(productIds)
        }
    }

    /**
     * Initiates the Google Play purchase flow for a specific product.
     */
    fun launchGooglePlayPurchase(activity: Activity, productDetails: ProductDetails) {
        billingClient.launchPurchaseFlow(activity, productDetails)
    }

    /**
     * Initiates the web-based payment flow for local providers like Payme.
     */
    fun createWebPayment(activity: Activity, provider: String, planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = paymentRepository.createPayment(provider, planId)) {
                is RepositoryResult.Success -> {
                    val paymentUrl = result.data.paymentUrl
                    // TODO: Persist the receiptId for verification upon app resume
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
     * Sends the purchase token to the backend for verification. If successful,
     * acknowledges the purchase with the Google Play Store.
     */
    private fun verifyAndAcknowledgePurchase(
        provider: String,
        token: String,
        planId: String,
        purchase: Purchase
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = subscriptionRepository.verifyPurchase(provider, token, planId)) {
                is RepositoryResult.Success -> {
                    Log.d(
                        "SubscriptionVM",
                        "Backend verification successful. Acknowledging purchase."
                    )
                    billingClient.acknowledgePurchase(purchase)
                    _uiState.update {
                        it.copy(isLoading = false, purchaseSuccessMessage = result.data.message)
                    }
                }

                is RepositoryResult.Error -> {
                    // If backend verification fails, do NOT acknowledge. The purchase will be re-processed later.
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    /**
     * Resets any displayed snackbar/toast messages.
     */
    fun clearMessages() {
        _uiState.update { it.copy(purchaseSuccessMessage = null, error = null) }
    }

    /**
     * Cleans up the BillingClient connection when the ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        billingClient.endConnection()
    }
}