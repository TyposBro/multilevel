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
import org.milliytechnology.spiko.features.billing.BillingClientWrapperImpl
import javax.inject.Inject

data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val productDetails: List<ProductDetails> = emptyList(),
    val purchaseSuccessMessage: String? = null,
    val error: String? = null,
    val paymentUrlToLaunch: String? = null,
    val userProfile: UserProfileViewData? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val billingClient: BillingClientWrapperImpl,
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

        // --- THIS IS THE FIX FOR THE LOADING BUG ---
        // This observer's ONLY job is to update the productDetails list.
        // It no longer touches the `isLoading` flag.
        billingClient.productDetails.onEach { products ->
            _uiState.update { it.copy(productDetails = products) }
        }.launchIn(viewModelScope)
        // --- END OF FIX ---

        billingClient.purchases.onEach { purchases ->
            purchases.forEach { purchase ->
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
                        _uiState.update { it.copy(error = "Purchase verification failed: Missing Product ID.") }
                    }
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
        billingClient.launchPurchaseFlow(activity, productDetails)
    }

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
                    Log.d("SubscriptionVM", "Backend verification successful. Acknowledging purchase.")
                    billingClient.acknowledgePurchase(purchase)
                    _uiState.update {
                        it.copy(isLoading = false, purchaseSuccessMessage = result.data.message)
                    }
                }
                is RepositoryResult.Error -> {
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