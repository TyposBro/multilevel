// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/AuthModel.kt
package com.typosbro.multilevel.data.remote.models

import com.google.gson.annotations.SerializedName

// --- Auth ---
data class AuthResponse(val _id: String, val email: String, val token: String)

// --- Social Auth ---
data class GoogleSignInRequest(val idToken: String)

data class UserProfileResponse(
    @SerializedName("_id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("createdAt") val createdAt: String // GSON will parse the date as a String
)

// This represents the JSON body: { "oneTimeToken": "some-uuid-string" }
data class OneTimeTokenRequest(val oneTimeToken: String)