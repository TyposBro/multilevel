package org.milliytechnology.spiko.features.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingClientWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails = _productDetails.asStateFlow()

    // Use a SharedFlow for purchases because they are events, not state.
    private val _purchases = MutableSharedFlow<List<Purchase>>()
    val purchases = _purchases.asSharedFlow()

    // --- NEW: A StateFlow to track the connection status ---
    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    // The listener for purchase updates
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // Emit new purchases to the flow to be handled by the ViewModel.
            _purchases.tryEmit(purchases)
        } else {
            Log.e("BillingClient", "Purchase update error: ${billingResult.debugMessage}")
        }
    }

    // Build the BillingClient with the listener
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingClient", "Billing Client connected")
                    // --- NEW: Update the connection status ---
                    _isReady.value = true
                    // After setup, query for any existing unconsumed purchases.
                    queryPurchases()
                } else {
                    Log.e(
                        "BillingClient",
                        "Billing Client connection failed: ${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingClient", "Billing Client disconnected. Trying to reconnect...")
                // --- NEW: Update the connection status ---
                _isReady.value = false
                // You might want to implement a retry policy here.
                startConnection()
            }
        })
    }

    /**
     * Queries product details from the Play Store. This is now a suspend function.
     */
    suspend fun queryProductDetails(productIds: List<String>) {
        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        // Use the suspend function `queryProductDetails` which returns a result directly.
        val productDetailsResult = billingClient.queryProductDetails(params)

        if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _productDetails.value = productDetailsResult.productDetailsList ?: emptyList()
        } else {
            Log.e(
                "BillingClient",
                "Failed to query products: ${productDetailsResult.billingResult.debugMessage}"
            )
        }
    }

    /**
     * Launches the Google Play purchase flow for a specific product.
     */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        // Find the specific offer token from the product details. For subscriptions,
        // it's common to use the first (and often only) offer.
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e("BillingClient", "No offer token found for product: ${productDetails.productId}")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken) // The offer token is required for subscriptions.
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Acknowledges a purchase to confirm it with the Play Store.
     * This is now a suspend function.
     */
    suspend fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            // Use the suspend function `acknowledgePurchase`.
            val billingResult = billingClient.acknowledgePurchase(params)

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingClient", "Purchase acknowledged successfully.")
            } else {
                Log.e(
                    "BillingClient",
                    "Failed to acknowledge purchase: ${billingResult.debugMessage}"
                )
            }
        }
    }

    /**
     * Queries for existing, unacknowledged purchases.
     * This now uses the suspend function `queryPurchasesAsync`.
     */
    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        // Use the callback-based version for non-suspend context
        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchasesList.isNotEmpty()) {
                    _purchases.tryEmit(purchasesList)
                }
            } else {
                Log.e(
                    "BillingClient",
                    "Failed to query existing purchases: ${billingResult.debugMessage}"
                )
            }
        }
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
            Log.d("BillingClient", "Billing Client connection closed.")
        }
    }
}