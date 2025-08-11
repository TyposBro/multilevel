package org.milliytechnology.spiko

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.milliytechnology.spiko.navigation.AppNavigation
import org.milliytechnology.spiko.ui.theme.AppTheme
import org.milliytechnology.spiko.ui.viewmodels.AuthViewModel
import org.milliytechnology.spiko.ui.viewmodels.ProfileViewModel
import org.milliytechnology.spiko.ui.viewmodels.SettingsViewModel

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    // Get a reference to the AuthViewModel to call the verification function
    private val authViewModel: AuthViewModel by viewModels()

    // Get a reference to the ProfileViewModel to trigger a profile refresh
    private val profileViewModel: ProfileViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {

            val isDarkTheme =
                settingsViewModel.isDarkTheme.collectAsStateWithLifecycle(initialValue = false).value
            val languageCode =
                settingsViewModel.currentLanguageCode.collectAsStateWithLifecycle().value


            LaunchedEffect(languageCode) {
                if (languageCode.isNotEmpty()) {
                    val appLocale = LocaleListCompat.forLanguageTags(languageCode)
                    AppCompatDelegate.setApplicationLocales(appLocale)
                }
            }

            AppTheme(darkTheme = isDarkTheme) {
                AppNavigation(authViewModel, profileViewModel, settingsViewModel)
            }
        }

        // Handle the deep link if the app was launched by it
        handleDeepLink(intent)
    }


    // --- CORRECTED onNewIntent ---
    // The `intent` parameter is non-nullable in the correct override signature.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Set the new intent as the activity's current intent
        setIntent(intent)
        // Handle the deep link from the new intent
        handleDeepLink(intent)
    }

    // --- CORRECTED handleDeepLink ---
    // This function can now safely accept a non-nullable Intent,
    // although keeping it nullable is fine for the onCreate case.
    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "multilevelapp" && data.host == "login") {
            val oneTimeToken = data.getQueryParameter("token")
            val paymentStatus = data.getQueryParameter("payment_status")

            if (!oneTimeToken.isNullOrBlank()) {
                // This is your existing logic for Telegram/Web login, which is correct.
                Log.d("DeepLink", "Received one-time token: $oneTimeToken")
                authViewModel.verifyOneTimeToken(oneTimeToken)

            } else if (paymentStatus == "success") {
                // This is the new logic for the successful Click payment callback.
                val transId = data.getQueryParameter("transaction_id")
                Log.d("DeepLink", "Received successful payment callback for transaction: $transId")

                // The backend webhook already updated the subscription.
                // We just need to refresh the user's profile to get the latest data.
                profileViewModel.fetchUserProfile()
            }
        }
    }
}