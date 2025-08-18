// android/app/src/main/java/org/milliytechnology/spiko/data/remote/models/PaymentModels.kt
package org.milliytechnology.spiko.data.remote.models

// Enhanced request for Click with optional payment method and phone number
data class CreatePaymentRequest(
    val provider: String,
    val planId: String,
    val paymentMethod: String? = null, // "web" or "invoice"
    val phoneNumber: String? = null // Required for invoice method
)

// Enhanced response for Click integration
data class CreatePaymentResponse(
    val success: Boolean,
    val provider: String,
    val paymentMethod: String? = null, // "web" or "invoice"
    val message: String? = null,
    
    // For web-based providers like Payme and Click web flow
    val paymentUrl: String? = null,
    val transactionId: String? = null,
    val clickTransactionId: String? = null,
    
    // For Click invoice
    val invoiceId: String? = null,
    
    // Legacy fields for backward compatibility
    val receiptId: String? = null,
    val merchantId: Long? = null,
    val serviceId: Long? = null,
    val merchantUserId: Long? = null,
    val amount: Double? = null,
    val transactionParam: String? = null
)

// Payment status check
data class PaymentStatusRequest(
    val transactionId: String
)

data class PaymentStatusResponse(
    val transactionId: String,
    val status: String, // "PENDING", "PREPARED", "COMPLETED", "FAILED"
    val provider: String,
    val planId: String,
    val amount: Long,
    val createdAt: String,
    val completedAt: String? = null,
    val providerTransactionId: String? = null,
    val externalStatus: Any? = null // Click API response if available
)

// Available payment methods
enum class PaymentProvider(val value: String) {
    CLICK("click"),
    PAYME("payme"),
    GOOGLE_PLAY("google")
}

enum class ClickPaymentMethod(val value: String, val displayName: String) {
    WEB("web", "Веб-оплата")
}

// Plan information
data class PaymentPlan(
    val id: String,
    val name: String,
    val priceUzs: Long, // Price in tiyin
    val priceSums: Double, // Price in sums for display
    val durationDays: Int,
    val tier: String,
    val description: String
) {
    companion object {
        val AVAILABLE_PLANS = listOf(
            PaymentPlan(
                id = "silver_monthly",
                name = "Серебряный план",
                priceUzs = 100000L, // 1000 UZS in tiyin
                priceSums = 1000.0,
                durationDays = 30,
                tier = "silver",
                description = "Доступ ко всем функциям на 1 месяц"
            )
            // Add more plans as needed
        )
    }
}