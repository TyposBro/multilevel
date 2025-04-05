package com.typosbro.multilevel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.typosbro.multilevel.navigation.AppNavigation // Import your AppNavigation
import com.typosbro.multilevel.ui.theme.MultilevelTheme

class MainActivity : ComponentActivity() {

    // Keep permission request logic here if needed globally,
    // but ChatDetailScreen now handles its own request.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Show a general message or let the specific screen handle it
            Toast.makeText(this, "Audio Permission Denied", Toast.LENGTH_SHORT).show()
        } else {
            // Permission granted - the screen needing it will react
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initial permission check (optional, could be deferred to the screen)
        checkAndRequestAudioPermission()

        setContent {
            MultilevelTheme {
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

    // Remove all Vosk-related properties and methods (model, listener, start/stop, etc.)
    // Remove messageList, partialText, isRecording state etc. - they belong in ViewModels.
}