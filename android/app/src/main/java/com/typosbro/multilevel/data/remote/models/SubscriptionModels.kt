package com.typosbro.multilevel.data.remote.models

// Request to send to our backend
data class VerifyPurchaseRequest(
    val provider: String,
    val token: String,
    val planId: String
)

// Response from our backend
data class SubscriptionResponse(
    val message: String,
    val subscription: Subscription
)

data class Subscription(
    val tier: String,
    val expiresAt: String?,
    val hasUsedGoldTrial: Boolean
)