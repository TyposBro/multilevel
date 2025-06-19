// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/MainActivity.kt

package com.typosbro.multilevel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.typosbro.multilevel.navigation.AppNavigation
import com.typosbro.multilevel.ui.theme.MultilevelTheme
import com.typosbro.multilevel.ui.viewmodels.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initial permission check (optional, could be deferred to the screen)
        checkAndRequestAudioPermission()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()

            MultilevelTheme(darkTheme = isDarkTheme) {
                AppNavigation() // Use the centralized navigation composable
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

