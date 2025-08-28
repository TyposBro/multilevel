package org.milliytechnology.spiko.features.billing

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// The contract for our Billing service
interface BillingClientWrapper {
    val productDetails: StateFlow<List<ProductDetails>>
    val purchases: SharedFlow<List<Purchase>>
    val isReady: StateFlow<Boolean>

    fun startConnection()
    suspend fun queryProductDetails(productIds: List<String>)
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, obfuscatedAccountId: String? = null)
    suspend fun acknowledgePurchase(purchase: Purchase)
    fun endConnection()
}