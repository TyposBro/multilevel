package org.milliytechnology.spiko.features.billing

import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A helper class for testing Google Play Billing functionality in debug builds.
 * Provides utilities to simulate different billing scenarios.
 */
@Singleton
class BillingTestHelper @Inject constructor(
    private val fakeBillingClient: FakeBillingClientWrapper
) {

    companion object {
        private const val TAG = "BillingTestHelper"
    }

    /**
     * Simulates a successful purchase for a given product
     */
    fun simulateSuccessfulPurchase(productId: String) {
        Log.d(TAG, "Simulating successful purchase for: $productId")
        
        val fakePurchaseJson = """
            {
                "purchaseToken": "fake_test_token_${System.currentTimeMillis()}",
                "orderId": "GPA.${System.currentTimeMillis()}-FAKE-ORDER",
                "products": ["$productId"],
                "purchaseState": 1,
                "acknowledged": false,
                "purchaseTime": ${System.currentTimeMillis()},
                "autoRenewing": true
            }
        """.trimIndent()

        val fakeSignature = "fake_signature_${System.currentTimeMillis()}"
        val fakePurchase = Purchase(fakePurchaseJson, fakeSignature)
        
        // Emit the purchase through the fake client
        (fakeBillingClient._purchases as MutableSharedFlow).tryEmit(listOf(fakePurchase))
    }

    /**
     * Simulates a failed purchase
     */
    fun simulateFailedPurchase(productId: String, errorCode: Int = 1) {
        Log.d(TAG, "Simulating failed purchase for: $productId with error code: $errorCode")
        // For failed purchases, we simply don't emit anything or emit an empty list
        (fakeBillingClient._purchases as MutableSharedFlow).tryEmit(emptyList())
    }

    /**
     * Simulates a pending purchase (common for slow payment methods)
     */
    fun simulatePendingPurchase(productId: String) {
        Log.d(TAG, "Simulating pending purchase for: $productId")
        
        val fakePurchaseJson = """
            {
                "purchaseToken": "fake_pending_token_${System.currentTimeMillis()}",
                "orderId": "GPA.${System.currentTimeMillis()}-PENDING-ORDER",
                "products": ["$productId"],
                "purchaseState": 2,
                "acknowledged": false,
                "purchaseTime": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        val fakeSignature = "fake_pending_signature"
        val fakePurchase = Purchase(fakePurchaseJson, fakeSignature)
        
        (fakeBillingClient._purchases as MutableSharedFlow).tryEmit(listOf(fakePurchase))
    }

    /**
     * Creates fake product details for testing
     */
    fun createFakeProductDetails(): List<ProductDetails> {
        Log.d(TAG, "Creating fake product details for testing")
        
        // Since ProductDetails is final and complex to mock, we'll return empty list
        // and rely on the UI handling this scenario gracefully
        return emptyList()
    }

    /**
     * Simulates various billing scenarios for comprehensive testing
     */
    fun runBillingScenarioTest(scenario: BillingTestScenario, productId: String = "silver_monthly") {
        Log.d(TAG, "Running billing scenario: ${scenario.name}")
        
        when (scenario) {
            BillingTestScenario.SUCCESSFUL_PURCHASE -> simulateSuccessfulPurchase(productId)
            BillingTestScenario.FAILED_PURCHASE -> simulateFailedPurchase(productId)
            BillingTestScenario.PENDING_PURCHASE -> simulatePendingPurchase(productId)
            BillingTestScenario.NETWORK_ERROR -> simulateNetworkError()
            BillingTestScenario.ALREADY_OWNED -> simulateAlreadyOwnedError(productId)
        }
    }

    private fun simulateNetworkError() {
        Log.d(TAG, "Simulating network error scenario")
        // Network errors typically result in no purchases being emitted
        (fakeBillingClient._purchases as MutableSharedFlow).tryEmit(emptyList())
    }

    private fun simulateAlreadyOwnedError(productId: String) {
        Log.d(TAG, "Simulating already owned error for: $productId")
        // For already owned, we simulate an existing purchase
        simulateSuccessfulPurchase(productId)
    }

    /**
     * Resets the billing state for fresh testing
     */
    fun resetBillingState() {
        Log.d(TAG, "Resetting billing state")
        (fakeBillingClient._productDetails as MutableStateFlow).value = emptyList()
        // Note: We can't easily clear the SharedFlow, but that's okay for testing
    }
}

/**
 * Different billing test scenarios
 */
enum class BillingTestScenario {
    SUCCESSFUL_PURCHASE,
    FAILED_PURCHASE,
    PENDING_PURCHASE,
    NETWORK_ERROR,
    ALREADY_OWNED
}
