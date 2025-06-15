// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/MainActivity.kt

package com.typosbro.multilevel

import ai.onnxruntime.OrtSession
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.typosbro.multilevel.navigation.AppNavigation // Import your AppNavigation
import com.typosbro.multilevel.ui.theme.MultilevelTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

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

}

