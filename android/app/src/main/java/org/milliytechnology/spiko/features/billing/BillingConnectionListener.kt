// in: /app/src/main/java/org/milliytechnology/spiko/features/billing/BillingConnectionListener.kt
package org.milliytechnology.spiko.features.billing

/**
 * Interface for receiving callbacks about the state of the connection
 * to the Google Play Billing service.
 */
interface BillingConnectionListener {
    /**
     * Called when the billing client setup process is complete.
     *
     * @param isSuccess True if the connection was established successfully, false otherwise.
     * @param message A descriptive message about the connection result.
     */
    fun onBillingClientConnected(isSuccess: Boolean, message: String)
}