package com.typosbro.multilevel.data.remote.models

// Request to our backend's /api/payment/create
data class CreatePaymentRequest(
    val provider: String,
    val planId: String
)

// Response from our backend's /api/payment/create
data class CreatePaymentResponse(
    val paymentUrl: String,
    val receiptId: String
)