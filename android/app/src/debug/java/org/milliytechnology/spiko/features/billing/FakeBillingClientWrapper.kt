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

    // Make these internal so BillingTestHelper can access them
    internal val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    override val productDetails = _productDetails.asStateFlow()

    internal val _purchases = MutableSharedFlow<List<Purchase>>()
    override val purchases = _purchases.asSharedFlow()

    override val isReady = MutableStateFlow(true) // Always ready instantly!

    // Control purchase behavior for testing
    var shouldSimulateError = false
    var purchaseDelay = 0L // Delay in milliseconds

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
        
        if (shouldSimulateError) {
            Log.d("FakeBillingClient", "Simulating purchase error")
            // Don't emit anything to simulate an error
            return
        }
        
        // Simulate purchase delay if configured
        if (purchaseDelay > 0) {
            Log.d("FakeBillingClient", "Simulating purchase delay of ${purchaseDelay}ms")
            // In a real scenario, you might use a coroutine here
        }
        
        // Simulate an immediate, successful purchase!
        val fakePurchaseJson = """
            {
                "purchaseToken": "fake_test_token_${System.currentTimeMillis()}",
                "orderId": "GPA.${System.currentTimeMillis()}-FAKE-ORDER",
                "products": ["${productDetails.productId}"],
                "purchaseState": 1,
                "acknowledged": false,
                "purchaseTime": ${System.currentTimeMillis()},
                "autoRenewing": true
            }
        """.trimIndent()

        val fakeSignature = "fake_signature_${System.currentTimeMillis()}"

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