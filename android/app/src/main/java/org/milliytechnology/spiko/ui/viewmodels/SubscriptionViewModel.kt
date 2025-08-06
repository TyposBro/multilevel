package org.milliytechnology.spiko.ui.viewmodels

import android.app.Activity
import android.util.Log
import androidx.fragment.app.FragmentActivity
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
import org.milliytechnology.spiko.data.repositories.PaymentRepository
import org.milliytechnology.spiko.data.repositories.SubscriptionRepository
import org.milliytechnology.spiko.features.billing.BillingClientWrapperImpl
import uz.click.mobilesdk.core.ClickMerchant
import uz.click.mobilesdk.core.ClickMerchantConfig
import uz.click.mobilesdk.core.callbacks.ClickMerchantListener
import uz.click.mobilesdk.impl.paymentoptions.ThemeOptions
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
    private val billingClient: BillingClientWrapperImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        billingClient.startConnection()

        // This coroutine waits for the connection to be ready before loading products.
        // This is the correct, safe way to do it.
        viewModelScope.launch {
            billingClient.isReady.filter { it }.first()
            Log.d("SubscriptionVM", "BillingClient is ready. Loading products.")
            loadProducts()
        }

        billingClient.productDetails.onEach { products ->
            _uiState.update { it.copy(isLoading = false, productDetails = products) }
        }.launchIn(viewModelScope)

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

    private fun loadProducts() {
        if (_uiState.value.productDetails.isNotEmpty()) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val productIds = listOf("silver_monthly", "gold_monthly")
            billingClient.queryProductDetails(productIds)
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
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun createClickPayment(activity: FragmentActivity, planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = paymentRepository.createPayment("click", planId)) {
                is RepositoryResult.Success -> {
                    val params = result.data
                    if (params.serviceId != null && params.merchantId != null && params.amount != null && params.transactionParam != null && params.merchantUserId != null) {
                        _uiState.update { it.copy(isLoading = false) }
                        launchClickSdk(activity, params)
                    }
                    // --- THIS IS THE FIX ---
                    // The `else` block was inside the `when` but should be outside
                    // to handle the case where the required params are null.
                    else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to get payment details from server."
                            )
                        }
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    private fun launchClickSdk(
        activity: FragmentActivity,
        params: org.milliytechnology.spiko.data.remote.models.CreatePaymentResponse
    ) {
        val config = ClickMerchantConfig.Builder()
            .serviceId(params.serviceId!!)
            .merchantId(params.merchantId!!)
            .merchantUserId(params.merchantUserId!!)
            .amount(params.amount!!)
            .transactionParam(params.transactionParam!!)
            .locale("EN")
            .theme(ThemeOptions.LIGHT)
            .build()

        ClickMerchant.init(activity.supportFragmentManager, config, object : ClickMerchantListener {
            override fun onReceiveRequestId(id: String) {
                Log.d("ClickSDK", "Request ID received: $id")
            }

            override fun onSuccess(paymentId: Long) {
                _uiState.update { it.copy(purchaseSuccessMessage = "Payment successful! Your subscription is being activated.") }
            }

            override fun onFailure() {
                _uiState.update { it.copy(error = "Payment failed or was cancelled.") }
            }

            override fun onInvoiceCancelled() {
                _uiState.update { it.copy(error = "Payment invoice was cancelled.") }
            }

            override fun closeDialog() {
                ClickMerchant.dismiss()
            }
        })
    }

    fun clearMessages() {
        _uiState.update { it.copy(purchaseSuccessMessage = null, error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        billingClient.endConnection()
    }
}