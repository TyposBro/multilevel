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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
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
    private var partialText by mutableStateOf("")
    private var isRecording by mutableStateOf(false)
    var displayedText by mutableStateOf("")

    // Keep track of completed messages
    private var messageList = mutableStateListOf<String>()


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
                    partialText = displayedText,
                    onStartMicRecognition = ::startMicRecognition,
                    onStopRecognition = ::stopRecognition,
                    isRecording = isRecording,
                    messageList = messageList
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
        partialText = ""
        isRecording = true
        voskManager?.startMicrophoneRecognition()
    }

    private fun stopRecognition() {
        // Capture final partial results
        if (partialText.isNotEmpty()) {
            recognitionResults += partialText
        }

        if (recognitionResults.isNotEmpty()) {
            messageList.add(0, recognitionResults.trim())
        }
        voskManager?.stopRecognition()
        resetStates()
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
                partialText = json.optString("partial", "") + " "  // Add trailing space
                updateDisplayText()
            } catch (e: Exception) {
                Log.e("VoskDebug", "Partial parse error", e)
            }
        }

        override fun onResult(hypothesis: String) {
            val fullText = JSONObject(hypothesis).optString("text", "")
            if (fullText.isNotEmpty()) {
                recognitionResults += "$fullText "
                partialText = ""
                updateDisplayText()
            }
        }

        override fun onFinalResult(hypothesis: String) {
//            // The final result gets added to the total results
//            val json = JSONObject(hypothesis)
//            val fullText = json.optString("text", "")
//            if (fullText.isNotEmpty()) {
//                recognitionResults += "$fullText\n"
//            }
////            partialResults = ""
        }

        override fun onError(e: Exception) {
            recognitionResults += "Error: ${e.message}\n"
            partialText = ""
        }

        override fun onTimeout() {
            recognitionResults += "Recognition timeout\n"
            partialText = ""
        }
    }

    private fun updateDisplayText() {
        displayedText = "$recognitionResults$partialText".trim()
        // Update UI here through mutable state
    }

    private fun resetStates() {
        recognitionResults = ""
        partialText = ""
        displayedText = ""
        isRecording = false
    }
}