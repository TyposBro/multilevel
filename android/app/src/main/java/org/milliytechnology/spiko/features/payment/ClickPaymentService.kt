package org.milliytechnology.spiko.features.payment

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import org.milliytechnology.spiko.data.remote.models.ClickPaymentMethod
import org.milliytechnology.spiko.data.remote.models.CreatePaymentRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClickPaymentService @Inject constructor() {

    /**
     * Opens Click payment URL in Chrome Custom Tabs or external browser
     */
    fun openClickPayment(context: Context, paymentUrl: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()

            customTabsIntent.launchUrl(context, Uri.parse(paymentUrl))
        } catch (e: Exception) {
            // Fallback to regular browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
            context.startActivity(intent)
        }
    }

    /**
     * Creates a payment request for Click web flow
     */
    fun createWebPaymentRequest(planId: String): CreatePaymentRequest {
        return CreatePaymentRequest(
            provider = "click",
            planId = planId,
            paymentMethod = ClickPaymentMethod.WEB.value
        )
    }

    /**
     * Extracts payment result from deep link
     */
    fun parsePaymentDeepLink(uri: Uri): PaymentResult? {
        return try {
            val paymentStatus = uri.getQueryParameter("payment_status")
            val transactionId = uri.getQueryParameter("transaction_id")

            if (paymentStatus != null && transactionId != null) {
                PaymentResult(
                    status = paymentStatus,
                    transactionId = transactionId
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

data class PaymentResult(
    val status: String,
    val transactionId: String
)
