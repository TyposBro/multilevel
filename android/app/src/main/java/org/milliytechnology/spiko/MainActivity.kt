package org.milliytechnology.spiko

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.milliytechnology.spiko.navigation.AppNavigation
import org.milliytechnology.spiko.ui.theme.AppTheme
import org.milliytechnology.spiko.ui.viewmodels.AuthViewModel
import org.milliytechnology.spiko.ui.viewmodels.SettingsViewModel

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    // Get a reference to the AuthViewModel to call the verification function
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {

            val isDarkTheme =
                settingsViewModel.isDarkTheme.collectAsStateWithLifecycle(initialValue = false).value
            val languageCode =
                settingsViewModel.currentLanguageCode.collectAsStateWithLifecycle().value


            LaunchedEffect(languageCode) {
                if (!languageCode.isNullOrEmpty()) {
                    val appLocale = LocaleListCompat.forLanguageTags(languageCode)
                    AppCompatDelegate.setApplicationLocales(appLocale)
                }
            }

            AppTheme(darkTheme = isDarkTheme) {
                AppNavigation()
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
            val token = data.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                Log.d("DeepLink", "Received one-time token: $token")
                // Call the ViewModel function to verify the token.
                // The ViewModel will handle the API call and update the session state,
                // and the UI will react automatically.
                authViewModel.verifyOneTimeToken(token)
            }
        }
    }
}