// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/TokenManager.kt
package com.typosbro.multilevel.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// This class is now just a simple, direct wrapper around EncryptedSharedPreferences.
class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val AUTH_TOKEN_KEY = "auth_token"
    }

    fun saveToken(token: String) {
        sharedPreferences.edit().putString(AUTH_TOKEN_KEY, token).apply()
    }

    fun getToken(): String? {
        return sharedPreferences.getString(AUTH_TOKEN_KEY, null)
    }

    fun clearToken() {
        sharedPreferences.edit().remove(AUTH_TOKEN_KEY).apply()
    }

    fun hasToken(): Boolean {
        return getToken() != null
    }

    // --- REMOVED THE tokenFlow callbackFlow ---
}