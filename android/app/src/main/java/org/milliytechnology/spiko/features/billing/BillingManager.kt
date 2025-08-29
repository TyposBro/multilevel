// in: /app/src/main/java/org/milliytechnology/spiko/features/billing/BillingManager.kt
package org.milliytechnology.spiko.features.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.repositories.SubscriptionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all Google Play Billing interactions for the Spiko app.
 *
 * This class is a Singleton that maintains a single, persistent connection to Google Play,
 * handles the purchase lifecycle, and communicates reactively with ViewModels via StateFlows.
 * It is designed to be the single source of truth for all billing-related operations.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subscriptionRepository: SubscriptionRepository
) {
    companion object {
        private const val TAG = "SpikoBillingManager"
    }

    // --- Public State Flows for UI Observation ---
    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails = _productDetails.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    // Single events for the UI to observe
    private val _purchaseSuccessEvent = MutableStateFlow<String?>(null)
    val purchaseSuccessEvent = _purchaseSuccessEvent.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent = _errorEvent.asStateFlow()

    // A dedicated scope for all billing operations, running on a background thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Listener for purchases initiated from within the app.
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    Log.d(TAG, "Purchase successful, processing ${purchases.size} item(s).")
                    processPurchases(purchases)
                }
            }
            BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "Purchase flow cancelled by user.")
                _errorEvent.value = "Purchase cancelled."
            }
            else -> {
                Log.e(TAG, "Purchase failed with error code ${billingResult.responseCode}: ${billingResult.debugMessage}")
                _errorEvent.value = "Purchase failed. Please try again."
            }
        }
    }

    // FIX 1: Correctly build PendingPurchasesParams for the builder.
    private val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(pendingPurchasesParams) // Pass the built object
        .build()

    /**
     * Starts the connection to Google Play Billing. Call this once when billing is needed.
     */
    fun startConnection() {
        if (billingClient.isReady) {
            Log.d(TAG, "BillingClient is already connected.")
            _isReady.value = true
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connection successful.")
                    _isReady.value = true
                    queryPurchases() // Sync any purchases made while the app was offline.
                } else {
                    Log.e(TAG, "BillingClient connection failed: ${billingResult.debugMessage}")
                    _isReady.value = false
                    _errorEvent.value = "Cannot connect to Google Play."
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "BillingClient disconnected. Attempting to reconnect...")
                _isReady.value = false
                startConnection() // Simple retry logic
            }
        })
    }

    /**
     * Fetches product details for the given list of subscription IDs.
     * Results are emitted via the `productDetails` StateFlow.
     */
    fun queryProductDetails(productIds: List<String>) {
        if (!billingClient.isReady) {
            _errorEvent.value = "Billing service not ready."
            return
        }

        scope.launch {
            val productList = productIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

            // FIX 2 & 3: Use the modern suspend function and handle its result.
            val result: ProductDetailsResult = billingClient.queryProductDetails(params)

            if (result.billingResult.responseCode == BillingResponseCode.OK) {
                val detailsList = result.productDetailsList ?: emptyList()
                Log.d(TAG, "Successfully fetched ${detailsList.size} product details.")
                _productDetails.value = detailsList
            } else {
                Log.e(TAG, "Failed to fetch product details: ${result.billingResult.debugMessage}")
                _errorEvent.value = "Could not load subscription plans."
            }
        }
    }

    /**
     * Initiates the Google Play purchase flow for a subscription.
     */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, obfuscatedAccountId: String) {
        if (!billingClient.isReady) {
            _errorEvent.value = "Billing service not ready."
            return
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e(TAG, "No offer token found for product: ${productDetails.productId}")
            _errorEvent.value = "This plan is not available for purchase."
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .setObfuscatedAccountId(obfuscatedAccountId)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Core logic: Processes new purchases by verifying with the backend BEFORE acknowledging.
     */
    private fun processPurchases(purchases: List<Purchase>) {
        scope.launch {
            purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                .forEach { purchase ->
                    val planId = purchase.products.firstOrNull()
                    if (planId == null) {
                        Log.e(TAG, "Purchase failed: Product ID missing.")
                        _errorEvent.value="Purchase verification failed: Missing product ID."
                        return@forEach
                    }

                    Log.d(TAG, "Verifying purchase with backend for plan: $planId")
                    when (val result = subscriptionRepository.verifyPurchase("google", purchase.purchaseToken, planId)) {
                        is RepositoryResult.Success -> {
                            Log.d(TAG, "Backend verification successful. Acknowledging with Google.")
                            acknowledgePurchase(purchase)
                            _purchaseSuccessEvent.value = result.data.message
                        }
                        is RepositoryResult.Error -> {
                            Log.e(TAG, "Backend verification failed: ${result.message}")
                            _errorEvent.value= "Purchase could not be verified with our server. Please contact support."
                        }
                    }
                }
        }
    }

    /**
     * Acknowledges a purchase with Google Play, marking the transaction as complete.
     */
    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = withContext(Dispatchers.IO) {
            billingClient.acknowledgePurchase(params)
        }
        if (result.responseCode == BillingResponseCode.OK) {
            Log.i(TAG, "Purchase acknowledged successfully with Google Play.")
        } else {
            Log.e(TAG, "Failed to acknowledge purchase with Google: ${result.debugMessage}")
        }
    }

    /**
     * Queries for existing purchases to sync state, e.g., on app start.
     */
    private fun queryPurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
                Log.d(TAG, "Found ${purchases.size} existing purchases to process.")
                processPurchases(purchases)
            }
        }
    }

    /**
     * Clears single-shot event states after they have been observed by the UI.
     */
    fun clearErrorEvent() { _errorEvent.value = null }
    fun clearSuccessEvent() { _purchaseSuccessEvent.value = null }
}