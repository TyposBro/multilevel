package org.milliytechnology.spiko.data.remote.models

import com.google.gson.annotations.SerializedName

// --- Auth Response ---
// email and firstName are now nullable to handle all provider types.
data class AuthResponse(
    @SerializedName("_id") val id: String,
    @SerializedName("email") val email: String?,
    @SerializedName("firstName") val firstName: String?,
    @SerializedName("token") val token: String
)

// --- Social Auth Requests ---
data class GoogleSignInRequest(val idToken: String)
data class OneTimeTokenRequest(val oneTimeToken: String)


// --- User Profile Response ---
// This model should also reflect that some fields can be null.
data class UserProfileResponse(
    @SerializedName("_id") val id: String,
    @SerializedName("email") val email: String?,
    @SerializedName("firstName") val firstName: String?,
    @SerializedName("telegramId") val telegramId: Long?,
    @SerializedName("username") val username: String?, // Telegram username
    @SerializedName("authProvider") val authProvider: String,
    @SerializedName("createdAt") val createdAt: String
)