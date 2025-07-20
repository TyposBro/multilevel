// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/TokenManager.kt
package org.milliytechnology.spiko.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// This class is now just a simple, direct wrapper around EncryptedSharedPreferences.
@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // --- THIS IS THE FIX ---
    // The SharedPreferences instance is now initialized lazily and with error handling.
    private val sharedPreferences: SharedPreferences by lazy {
        createEncryptedSharedPreferences()
    }

    companion object {
        private const val AUTH_TOKEN_KEY = "auth_token"
        private const val PREFS_FILENAME = "auth_prefs"
    }

    private fun createEncryptedSharedPreferences(): SharedPreferences {
        try {
            // Try to create/open the EncryptedSharedPreferences as normal.
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // If any exception occurs (like AEADBadTagException), it means the file is corrupt or unreadable.
            Log.e(
                "TokenManager",
                "Failed to create/read EncryptedSharedPreferences. Deleting and recreating.",
                e
            )

            // Manually delete the corrupt preferences file.
            val prefsFile = File(context.filesDir.parent, "shared_prefs/$PREFS_FILENAME.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
            }

            // Retry creating the SharedPreferences. If this fails, the app will crash, which is acceptable
            // at this point as it indicates a more severe, unrecoverable device/system issue.
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
    // --- END OF FIX ---

    fun saveToken(token: String) {
        sharedPreferences.edit { putString(AUTH_TOKEN_KEY, token) }
    }

    fun getToken(): String? {
        return sharedPreferences.getString(AUTH_TOKEN_KEY, null)
    }

    fun clearToken() {
        sharedPreferences.edit { remove(AUTH_TOKEN_KEY) }
    }

    fun hasToken(): Boolean {
        return getToken() != null
    }
}