package org.milliytechnology.spiko.features.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// A fake implementation that gives you full control for local testing
@Singleton
class FakeBillingClientWrapper @Inject constructor() : BillingClientWrapper {

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    override val productDetails = _productDetails.asStateFlow()

    private val _purchases = MutableSharedFlow<List<Purchase>>()
    override val purchases = _purchases.asSharedFlow()

    override val isReady = MutableStateFlow(true) // Always ready instantly!

    override fun startConnection() {
        Log.d("FakeBillingClient", "startConnection() called. Instantly ready.")
    }

    override suspend fun queryProductDetails(productIds: List<String>) {
        // For local testing, we don't need real product details.
        // The main goal is to test the purchase flow itself.
        Log.d("FakeBillingClient", "queryProductDetails called. Emitting empty list.")
        _productDetails.value = emptyList()
    }

    override fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        Log.d("FakeBillingClient", "launchPurchaseFlow called for ${productDetails.productId}")
        // Simulate an immediate, successful purchase!
        val fakePurchaseJson = """
            {
                "purchaseToken": "fake_test_token_for_${productDetails.productId}",
                "orderId": "GPA.1234-FAKE-ORDER",
                "products": ["${productDetails.productId}"],
                "purchaseState": 1,
                "acknowledged": false
            }
        """.trimIndent()

        val fakeSignature = "fake_signature" // The signature doesn't matter for the fake

        val fakePurchase = Purchase(fakePurchaseJson, fakeSignature)

        Log.d("FakeBillingClient", "Emitting fake successful purchase.")
        _purchases.tryEmit(listOf(fakePurchase))
    }

    override suspend fun acknowledgePurchase(purchase: Purchase) {
        Log.d(
            "FakeBillingClient",
            "Acknowledged fake purchase with token: ${purchase.purchaseToken}"
        )
    }

    override fun endConnection() {
        Log.d("FakeBillingClient", "endConnection() called.")
    }
}