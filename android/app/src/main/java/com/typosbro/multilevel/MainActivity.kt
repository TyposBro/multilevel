package com.typosbro.multilevel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.navigation.AppNavigation
import com.typosbro.multilevel.ui.theme.MultilevelTheme
import com.typosbro.multilevel.ui.viewmodels.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initial permission check (optional, could be deferred to the screen)
        checkAndRequestAudioPermission()

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

            MultilevelTheme(darkTheme = isDarkTheme) {
                AppNavigation()
            }
        }
    }

    private fun checkAndRequestAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            // Optional: Explain why permission is needed before launching
            // shouldShowRequestPermissionRationale(...)
            else -> {
                // Launch the permission request
                // requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                // Let ChatDetailScreen handle the request when needed
            }
        }
    }
}