package com.typosbro.multilevel.ui.screens.practice

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.data.remote.models.CueCard
import com.typosbro.multilevel.ui.component.HandleAppLifecycle
import com.typosbro.multilevel.ui.component.RecognitionControls
import com.typosbro.multilevel.ui.viewmodels.ExamPart
import com.typosbro.multilevel.ui.viewmodels.ExamUiState
import com.typosbro.multilevel.ui.viewmodels.ExamViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    onNavigateToResults: (resultId: String) -> Unit,
    viewModel: ExamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()


    HandleAppLifecycle(onStop = viewModel::stopExam)
    // --- PERMISSION HANDLING LOGIC ---
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasAudioPermission = isGranted
            if (!isGranted) {
                coroutineScope.launch {
                    snackBarHostState.showSnackbar(
                        "Microphone permission is required to take the exam.",
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    )

    val requestPermission: () -> Unit = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // [FINAL UI FIX]
    // The key for LaunchedEffect is the specific piece of data that triggers the effect.
    // This ensures the effect runs *exactly* when `finalResultId` changes from null to a value.
    LaunchedEffect(uiState.finalResultId) {
        // Add logging to confirm this block runs.
        Log.d("ExamScreen", "LaunchedEffect triggered. finalResultId is: ${uiState.finalResultId}")
        uiState.finalResultId?.let { resultId ->
            Log.d("ExamScreen", "finalResultId is not null. Navigating with: $resultId")
            onNavigateToResults(resultId)
            viewModel.onNavigationToResultConsumed()
        }
    }

    // This effect shows a snackbar when an error occurs.
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackBarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState.currentPart) {
                            ExamPart.NOT_STARTED -> "IELTS Speaking Test"
                            ExamPart.PART_1 -> "IELTS Speaking Test - Part 1"
                            ExamPart.PART_2_PREP -> "IELTS Speaking Test - Part 2 (Preparation)"
                            ExamPart.PART_2_SPEAKING -> "IELTS Speaking Test - Part 2 (Speaking)"
                            ExamPart.PART_3 -> "IELTS Speaking Test - Part 3"
                            ExamPart.FINISHED -> "IELTS Speaking Test - Finished"
                            ExamPart.ANALYSIS_COMPLETE -> "IELTS Speaking Test - Analysis"
                        }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = uiState.currentPart,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ExamPartAnimation"
            ) { targetPart ->
                val onStartRecording = {
                    if (hasAudioPermission) {
                        viewModel.startUserSpeechRecognition()
                    } else {
                        requestPermission()
                    }
                }
                val onStopRecording = { viewModel.stopUserSpeechRecognition() }

                when (targetPart) {
                    ExamPart.NOT_STARTED -> NotStartedView(onStart = {
                        if (hasAudioPermission) {
                            viewModel.startExam()
                        } else {
                            requestPermission()
                        }
                    })

                    ExamPart.PART_1 -> ExaminerInteractionView(
                        uiState = uiState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        partTitle = "Part 1: Introduction and Interview"
                    )

                    ExamPart.PART_2_PREP -> Part2PrepView(uiState)

                    ExamPart.PART_2_SPEAKING -> Part2SpeakingView(
                        uiState = uiState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording
                    )

                    ExamPart.PART_3 -> ExaminerInteractionView(
                        uiState = uiState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        partTitle = "Part 3: Discussion"
                    )

                    ExamPart.FINISHED, ExamPart.ANALYSIS_COMPLETE -> AnalysisView(uiState)
                }
            }
        }
    }
}

@Composable
fun NotStartedView(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            "Welcome to the IELTS Speaking Test",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "This test consists of 3 parts and will take 11-14 minutes:\n\n" +
                    "• Part 1: Introduction and Interview (4-5 minutes)\n" +
                    "• Part 2: Long Turn with preparation (3-4 minutes)\n" +
                    "• Part 3: Discussion (4-5 minutes)",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Ready to begin your mock exam?",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart) {
            Text("Start Exam")
        }
    }
}

@Composable
fun ExaminerInteractionView(
    uiState: ExamUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    partTitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section with part title, timer and examiner text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = partTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Show timer for Part 1 and Part 3
            if (uiState.currentPart == ExamPart.PART_1 || uiState.currentPart == ExamPart.PART_3) {
                AnimatedVisibility(visible = uiState.timerValue > 0) {
                    Text(
                        text = "Time remaining: ${formatTime(uiState.timerValue)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.timerValue <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Please wait...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = uiState.examinerMessage ?: "...",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom section with user transcription and controls
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = uiState.partialTranscription.takeIf { it.isNotBlank() }
                    ?: if (uiState.isReadyForUserInput) "You can speak now..." else "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.heightIn(min = 48.dp),
                textAlign = TextAlign.Center,
                color = if (uiState.isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            // Show controls only when ready for user input or when recording
            AnimatedVisibility(visible = uiState.isReadyForUserInput || uiState.isRecording) {
                RecognitionControls(
                    isRecording = uiState.isRecording,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    enabled = true // Always enable controls when visible
                )
            }
        }
    }
}

@Composable
fun Part2PrepView(uiState: ExamUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Part 2: Long Turn - Preparation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Getting your topic...", style = MaterialTheme.typography.bodyLarge)
        } else {
            // Display the instructions spoken by the examiner
            Text(
                text = uiState.examinerMessage ?: "You have one minute to prepare your talk.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Preparation timer
            if (uiState.timerValue > 0) {
                Text(
                    text = formatTime(uiState.timerValue),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (uiState.timerValue <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))

            // Show cue card
            uiState.part2CueCard?.let {
                CueCardView(cueCard = it)
            }
        }
    }
}

@Composable
fun Part2SpeakingView(
    uiState: ExamUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section with timer and cue card
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Part 2: Long Turn - Speaking",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Speaking timer
            AnimatedVisibility(visible = uiState.timerValue > 0) {
                Text(
                    text = "Time remaining: ${formatTime(uiState.timerValue)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.timerValue <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))

            // Show examiner message or instruction
            Text(
                text = uiState.examinerMessage
                    ?: "Please speak about your topic for up to 2 minutes.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Show cue card
            uiState.part2CueCard?.let {
                CueCardView(cueCard = it)
            }
        }

        // Bottom section with user transcription and controls
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = uiState.partialTranscription.takeIf { it.isNotBlank() }
                    ?: if (uiState.isRecording) "Listening..." else "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.heightIn(min = 48.dp),
                textAlign = TextAlign.Center,
                color = if (uiState.isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            // Show controls when recording (Part 2 auto-starts but user can stop)
            AnimatedVisibility(visible = uiState.isReadyForUserInput || uiState.isRecording) {
                RecognitionControls(
                    isRecording = uiState.isRecording,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    enabled = true
                )
            }
        }
    }
}

@Composable
fun CueCardView(cueCard: CueCard) {
    Card(
        modifier = Modifier.padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Topic:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                cueCard.topic,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "You should talk about:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            cueCard.points.forEach { point ->
                Text(
                    "• $point",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AnalysisView(uiState: ExamUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center, // Center the content
        modifier = Modifier
            .fillMaxSize() // Ensure it fills the screen
            .padding(16.dp)
    ) {
        Text(
            "Exam Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))

        // This logic now clearly separates the different final states
        when {
            // State 1: Analysis is happening
            uiState.isLoading -> {
                Text(
                    "Analyzing your performance...",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            // State 2: Analysis failed
            uiState.error != null -> {
                Text(
                    text = "Analysis Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            // State 3: Analysis is done, about to navigate
            uiState.finalResultId != null -> {
                Text(
                    "Analysis complete!",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator() // Show a spinner to indicate action
                Spacer(Modifier.height(8.dp))
                Text("Redirecting to your results...")
            }
        }
    }
}

// Helper function to format time as MM:SS
private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}