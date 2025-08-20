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
class BillingClientWrapperImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BillingClientWrapper { // <-- Implement the interface

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    override val productDetails = _productDetails.asStateFlow()

    private val _purchases = MutableSharedFlow<List<Purchase>>()
    override val purchases = _purchases.asSharedFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady = _isReady.asStateFlow()

    // Error tracking
    private var connectionRetryCount = 0
    private val maxRetryCount = 3

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    Log.d("BillingClient", "Purchase update successful: ${purchases.size} purchases")
                    _purchases.tryEmit(purchases)
                } else {
                    Log.w("BillingClient", "Purchase update successful but purchases list is null")
                    _purchases.tryEmit(emptyList())
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d("BillingClient", "Purchase canceled by user")
                // Don't treat as error, user intentionally canceled
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.w("BillingClient", "Item already owned")
                // Query existing purchases to refresh state
                queryPurchases()
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                Log.e("BillingClient", "Developer error in purchase flow: ${billingResult.debugMessage}")
            }
            else -> {
                Log.e("BillingClient", "Purchase update error (${billingResult.responseCode}): ${billingResult.debugMessage}")
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    override fun startConnection() {
        if (billingClient.isReady) {
            Log.d("BillingClient", "Billing Client is already connected.")
            _isReady.value = true
            connectionRetryCount = 0 // Reset retry count on successful connection
            return
        }

        Log.d("BillingClient", "Starting billing client connection (attempt ${connectionRetryCount + 1}/$maxRetryCount)")

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        Log.d("BillingClient", "Billing Client connected successfully.")
                        _isReady.value = true
                        connectionRetryCount = 0 // Reset retry count on success
                        queryPurchases()
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        Log.e("BillingClient", "Billing unavailable on this device")
                        _isReady.value = false
                    }
                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                        Log.e("BillingClient", "Google Play Store service is unavailable")
                        _isReady.value = false
                        attemptRetryConnection()
                    }
                    else -> {
                        Log.e("BillingClient", "Billing setup failed (${billingResult.responseCode}): ${billingResult.debugMessage}")
                        _isReady.value = false
                        attemptRetryConnection()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingClient", "Billing service disconnected")
                _isReady.value = false
                attemptRetryConnection()
            }
        })
    }

    private fun attemptRetryConnection() {
        if (connectionRetryCount < maxRetryCount) {
            connectionRetryCount++
            Log.d("BillingClient", "Attempting to reconnect... (${connectionRetryCount}/$maxRetryCount)")
            // Simple delay before retry (in a real app, you might want exponential backoff)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startConnection()
            }, 1000L * connectionRetryCount) // Increasing delay: 1s, 2s, 3s
        } else {
            Log.e("BillingClient", "Max retry attempts reached. Billing will remain unavailable.")
        }
    }

    override suspend fun queryProductDetails(productIds: List<String>) {
        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
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

    override fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e("BillingClient", "No offer token found for product: ${productDetails.productId}")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override suspend fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
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

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

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

    override fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
            Log.d("BillingClient", "Billing Client connection closed.")
        }
    }
}