// Create new file: ui/screens/practice/ExamScreen.kt
package com.typosbro.multilevel.ui.screens.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.data.remote.models.CueCard
import com.typosbro.multilevel.ui.component.RecognitionControls
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider
import com.typosbro.multilevel.ui.viewmodels.ExamPart
import com.typosbro.multilevel.ui.viewmodels.ExamUiState
import com.typosbro.multilevel.ui.viewmodels.ExamViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    onNavigateToResults: (resultId: String) -> Unit,
    viewModel: ExamViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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
                // Optionally show a message if permission is denied
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Microphone permission is required to take the exam.",
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    )

    // A helper function to request permission if needed
    val requestPermission: () -> Unit = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(uiState.finalResultId) {
        uiState.finalResultId?.let { onNavigateToResults(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("IELTS Speaking Test") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) } // Add snackbar host
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
                }
            ) { targetPart ->
                when (targetPart) {
                    ExamPart.NOT_STARTED -> NotStartedView(onStart = {
                        if (hasAudioPermission) {
                            viewModel.startExam()
                        } else {
                            requestPermission()
                        }
                    })

                    ExamPart.PART_1, ExamPart.PART_3 -> ExaminerInteractionView(
                        uiState = uiState,
                        onStartRecording = {
                            if (hasAudioPermission) {
                                viewModel.startUserSpeechRecognition()
                            } else {
                                requestPermission()
                            }
                        },
                        onStopRecording = { viewModel.stopUserSpeechRecognition() })

                    ExamPart.PART_2_SPEAKING -> Part2SpeakingView(
                        uiState = uiState,
                        onStartRecording = {
                            if (hasAudioPermission) {
                                viewModel.startUserSpeechRecognition()
                            } else {
                                requestPermission()
                            }
                        },
                        onStopRecording = { viewModel.stopUserSpeechRecognition() })

                    ExamPart.PART_2_PREP -> Part2PrepView(uiState)
                    ExamPart.FINISHED, ExamPart.ANALYSIS_COMPLETE -> AnalysisView()
                }
            }
        }
    }
}

@Composable
fun NotStartedView(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ready to begin your mock exam?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart) {
            Text("Start Exam")
        }
    }
}

@Composable
fun ExaminerInteractionView(
    uiState: ExamUiState,
    onStartRecording: () -> Unit, // Pass as a lambda
    onStopRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Examiner Text
        Text(
            text = uiState.examinerMessage ?: "...",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        // User Input Area
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = uiState.partialTranscription,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.heightIn(min = 48.dp)
            )
            Spacer(Modifier.height(16.dp))
            RecognitionControls(
                isRecording = uiState.isUserListening,
                onStartRecording = onStartRecording, // Use the passed lambda
                onStopRecording = onStopRecording
            )
        }
    }
}

@Composable
fun Part2PrepView(uiState: ExamUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Part 2: Cue Card", style = MaterialTheme.typography.headlineMedium)
        Text("You have one minute to prepare.", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "${uiState.timerValue}s",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        uiState.part2CueCard?.let {
            CueCardView(cueCard = it)
        }
    }
}

@Composable
fun Part2SpeakingView(
    uiState: ExamUiState,
    onStartRecording: () -> Unit, // Pass as a lambda
    onStopRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Time remaining: ${uiState.timerValue}s",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(16.dp))
            uiState.part2CueCard?.let { CueCardView(cueCard = it) }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = uiState.partialTranscription,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.heightIn(min = 48.dp)
            )
            Spacer(Modifier.height(16.dp))
            RecognitionControls(
                isRecording = uiState.isUserListening,
                onStartRecording = onStartRecording, // Use the passed lambda
                onStopRecording = onStopRecording
            )
        }
    }
}

@Composable
fun CueCardView(cueCard: CueCard) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(cueCard.topic, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            cueCard.points.forEach { point ->
                Text("• $point", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AnalysisView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Exam Finished!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Analyzing your performance...", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator()
    }
}