// android/app/src/main/java/org/milliytechnology/spiko/data/remote/models/PaymentModels.kt
package org.milliytechnology.spiko.data.remote.models

// Request remains the same
data class CreatePaymentRequest(
    val provider: String,
    val planId: String
)

// Response is now more flexible
data class CreatePaymentResponse(
    // For web-based providers like Payme
    val paymentUrl: String?,
    val receiptId: String?,

    // For Click SDK
    val merchantId: Long?,
    val serviceId: Long?,
    val merchantUserId: Long?,
    val amount: Double?,
    val transactionParam: String?
)