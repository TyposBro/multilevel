// android/app/src/main/java/org/milliytechnology/spiko/ui/viewmodels/SubscriptionViewModel.kt

package org.milliytechnology.spiko.ui.viewmodels

import android.app.Activity
import android.util.Log
// REMOVE this import as we need the more specific one
// import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity // IMPORT THIS
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
    private val billingClient: BillingClientWrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        billingClient.startConnection()

        billingClient.productDetails.onEach { products ->
            _uiState.update { it.copy(productDetails = products) }
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
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    fun loadProducts() {
        viewModelScope.launch {
            val productIds = listOf("silver_monthly", "gold_monthly")
            billingClient.queryProductDetails(productIds)
        }
    }

    fun launchGooglePlayPurchase(activity: Activity, productDetails: ProductDetails) {
        billingClient.launchPurchaseFlow(activity, productDetails)
    }

    fun createWebPayment(activity: Activity, provider: String, planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = paymentRepository.createPayment(provider, planId)) {
                is RepositoryResult.Success -> {
                    val paymentUrl = result.data.paymentUrl
                    if (paymentUrl != null) {
                        openUrlInCustomTab(activity, paymentUrl)
                    }
                    _uiState.update { it.copy(isLoading = false) }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
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

    // CHANGE THE PARAMETER TYPE HERE
    fun createClickPayment(activity: FragmentActivity, planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = paymentRepository.createPayment("click", planId)) {
                is RepositoryResult.Success -> {
                    val params = result.data
                    if (params.serviceId != null && params.merchantId != null && params.amount != null && params.transactionParam != null && params.merchantUserId != null) {
                        _uiState.update { it.copy(isLoading = false) }
                        launchClickSdk(activity, params)
                    } else {
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

    // AND CHANGE THE PARAMETER TYPE HERE
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

        ClickMerchant.init(
            activity.supportFragmentManager,
            config,
            object : ClickMerchantListener {
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
            }
        )
    }

    fun clearMessages() {
        _uiState.update { it.copy(purchaseSuccessMessage = null, error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        billingClient.endConnection()
    }
}