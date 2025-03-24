package com.typosbro.multilevel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.typosbro.multilevel.features.vosk.VoskRecognitionManager
import com.typosbro.multilevel.ui.theme.MultilevelTheme
import org.json.JSONObject
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.StorageService

class MainActivity : ComponentActivity() {



    private var voskManager: VoskRecognitionManager? = null
    private var model by mutableStateOf<Model?>(null)
    private var recognitionResults by mutableStateOf("")
    private var partialResults by mutableStateOf("")
    private var isPaused by mutableStateOf(false)
    // Keep track of completed messages
    private var completedMessages = mutableStateListOf<String>()


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initModel()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check for audio recording permission
        checkPermissionAndInitModel()

        setContent {
            MultilevelTheme {
                val navController = rememberNavController()

                AppScaffold(
                    navController = navController,
                    partialResults = partialResults,
                    onStartMicRecognition = ::startMicRecognition,
                    onStopRecognition = ::stopRecognition,
                    isPaused = isPaused,
                    completedMessages = completedMessages
                )
            }
        }
    }

    private fun checkPermissionAndInitModel() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initModel()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun initModel() {
        StorageService.unpack(this, "model-en-us", "model",
            { unpackedModel ->
                model = unpackedModel
                // Create the Vosk manager when model is ready
                voskManager = VoskRecognitionManager(this, unpackedModel, recognitionListener)
            },
            { exception ->
                Toast.makeText(
                    this,
                    "Failed to unpack the model: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun startMicRecognition() {
        // Clear previous results when starting a new recognition session
        recognitionResults = ""
        partialResults = ""
        isPaused = true
        voskManager?.startMicrophoneRecognition()
    }

    private fun stopRecognition() {
        if (recognitionResults.isNotEmpty() && partialResults.isEmpty()) {
            // Only add if it's not already in the list and is not empty
            Log.d("VoiceRecognitionScreen", "Adding completed message: $recognitionResults")
            val trimmedResults = recognitionResults.trim()
            if (trimmedResults.isNotEmpty()) {
                completedMessages.add(0, trimmedResults)
            }
        }
        isPaused = false
        recognitionResults = ""
        partialResults = ""
        voskManager?.stopRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any speech services
        stopRecognition()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            try {
                val json = JSONObject(hypothesis)
                partialResults = json.optString("partial", "")
                Log.d("VoskDebug", "Partial text: $partialResults")
            } catch (e: Exception) {
                Log.e("VoskDebug", "Partial parse error", e)
                partialResults = ""
            }
        }

        override fun onResult(hypothesis: String) {
            try {
                val json = JSONObject(hypothesis)
                val fullText = json.optString("text", "")
                if (fullText.isNotEmpty()) {
                    recognitionResults += "$fullText\n"
                }
            } catch (e: Exception) {
                Log.e("VoskDebug", "Result parse error", e)
            }
            partialResults = ""
        }

        override fun onFinalResult(hypothesis: String) {
            // The final result gets added to the total results
            val json = JSONObject(hypothesis)
            val fullText = json.optString("text", "")
            if (fullText.isNotEmpty()) {
                recognitionResults += "$fullText\n"
            }
            partialResults = ""
        }

        override fun onError(e: Exception) {
            recognitionResults += "Error: ${e.message}\n"
            partialResults = ""
        }

        override fun onTimeout() {
            recognitionResults += "Recognition timeout\n"
            partialResults = ""
        }
    }
}