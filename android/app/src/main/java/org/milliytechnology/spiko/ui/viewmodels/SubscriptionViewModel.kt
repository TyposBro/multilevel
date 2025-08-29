// in: /app/src/main/java/org/milliytechnology/spiko/ui/viewmodels/SubscriptionViewModel.kt

package org.milliytechnology.spiko.ui.viewmodels

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.repositories.AuthRepository
import org.milliytechnology.spiko.data.repositories.PaymentRepository
import org.milliytechnology.spiko.features.billing.BillingManager
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
    private val billingManager: BillingManager,
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        initializeBillingAndProfile()
        observeBillingManagerEvents()
    }

    private fun initializeBillingAndProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            fetchUserProfile()
            billingManager.startConnection()
        }
    }

    private fun observeBillingManagerEvents() {
        billingManager.isReady.onEach { isReady ->
            if (isReady) {
                Log.d("BillingTraceVM", "Billing connection successful.")
                // Now that we're connected, trigger the product query.
                // The result will be observed via the `productDetails` flow below.
                loadProducts()
            }
        }.launchIn(viewModelScope)

        billingManager.productDetails.onEach { details ->
            if (details.isNotEmpty()) {
                Log.d("BillingTraceVM", "Observed ${details.size} product details.")
                _uiState.update { it.copy(productDetails = details, isLoading = false) }
            }
        }.launchIn(viewModelScope)

        billingManager.purchaseSuccessEvent.onEach { message ->
            message?.let {
                _uiState.update { it.copy(purchaseSuccessMessage = message, isLoading = false) }
                onPurchaseCompleted()
                billingManager.clearSuccessEvent()
            }
        }.launchIn(viewModelScope)

        billingManager.errorEvent.onEach { error ->
            error?.let {
                _uiState.update { it.copy(error = error, isLoading = false) }
                billingManager.clearErrorEvent()
            }
        }.launchIn(viewModelScope)
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            when (val result = authRepository.getUserProfile()) {
                is RepositoryResult.Success -> {
                    Log.d("BillingTraceVM", "Profile fetch success. UserID: ${result.data.id}")
                    _uiState.update {
                        it.copy(userProfile = result.data.toViewData())
                    }
                }
                is RepositoryResult.Error -> {
                    Log.e("BillingTraceVM", "Profile fetch error: ${result.message}")
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    /**
     * Triggers a query for product details from the BillingManager.
     * The results will be delivered through the `billingManager.productDetails` StateFlow,
     * which is already being observed by the ViewModel.
     */
    private fun loadProducts() {
        val productIds = listOf("silver_monthly", "gold_monthly")
        Log.d("BillingTraceVM", "Requesting product details query for: $productIds")
        // THE FIX: Simply call the method without the listener.
        billingManager.queryProductDetails(productIds)
    }

    fun launchGooglePlayPurchase(activity: Activity, productDetails: ProductDetails) {
        val userId = _uiState.value.userProfile?.id
        if (userId == null) {
            _uiState.update { it.copy(error = "User is not logged in. Cannot make a purchase.") }
            return
        }
        Log.d("BillingTraceVM", "Launching purchase flow for product: ${productDetails.productId} for user: $userId")
        billingManager.launchPurchaseFlow(activity, productDetails, userId)
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
}