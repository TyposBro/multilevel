// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/models/AuthModel.kt
package com.typosbro.multilevel.data.remote.models

import com.google.gson.annotations.SerializedName

// --- Auth ---
data class AuthRequest(val email: String, val password: String)
data class AuthResponse(val _id: String, val email: String, val token: String)

data class UserProfileResponse(
    @SerializedName("_id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("createdAt") val createdAt: String // GSON will parse the date as a String
)