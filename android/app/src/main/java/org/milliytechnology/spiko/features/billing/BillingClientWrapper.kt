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

    private val _purchases = MutableSharedFlow<List<Purchase>>()
    val purchases = _purchases.asSharedFlow()

    // --- THIS IS THE NEW, CRITICAL ADDITION ---
    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()
    // --- END OF ADDITION ---

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            _purchases.tryEmit(purchases)
        } else {
            Log.e("BillingClient", "Purchase update error: ${billingResult.debugMessage}")
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

    fun startConnection() {
        if (billingClient.isReady) {
            Log.d("BillingClient", "Billing Client is already connected.")
            _isReady.value = true // Ensure state is correct if already connected
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingClient", "Billing Client connected successfully.")
                    _isReady.value = true // --- THIS IS THE FIX ---
                    queryPurchases()
                } else {
                    Log.e(
                        "BillingClient",
                        "Billing Client connection failed: ${billingResult.debugMessage}"
                    )
                    _isReady.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingClient", "Billing Client disconnected. Trying to reconnect...")
                _isReady.value = false
                startConnection()
            }
        })
    }

    suspend fun queryProductDetails(productIds: List<String>) {
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

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
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

    suspend fun acknowledgePurchase(purchase: Purchase) {
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

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
            Log.d("BillingClient", "Billing Client connection closed.")
        }
    }
}