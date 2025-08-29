// in: /app/src/main/java/org/milliytechnology/spiko/features/billing/BillingProductDetailsListener.kt
package org.milliytechnology.spiko.features.billing

import com.android.billingclient.api.ProductDetails

/**
 * Interface for receiving the results of a product details query from Google Play.
 */
interface BillingProductDetailsListener {
    /**
     * Called when product details are successfully fetched.
     *
     * @param productDetails A list of [ProductDetails] objects matching the queried product IDs.
     */
    fun onSuccess(productDetails: List<ProductDetails>)

    /**
     * Called when there is an error fetching product details.
     *
     * @param message A descriptive error message.
     */
    fun onError(message: String)
}