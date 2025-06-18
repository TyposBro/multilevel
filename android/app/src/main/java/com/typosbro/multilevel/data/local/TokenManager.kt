// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/TokenManager.kt
package com.typosbro.multilevel.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

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

    // --- NEW FLOW TO OBSERVE TOKEN CHANGES ---
    val tokenFlow: Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AUTH_TOKEN_KEY) {
                trySend(getToken())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        // Send the initial value
        trySend(getToken())

        // Unregister the listener when the flow is cancelled
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.conflate() // Use conflate to only emit the latest value if the collector is slow
}