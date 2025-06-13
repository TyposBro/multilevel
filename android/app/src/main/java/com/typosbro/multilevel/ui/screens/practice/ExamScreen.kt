// Create new file: ui/screens/practice/ExamScreen.kt
package com.typosbro.multilevel.ui.screens.practice

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.data.remote.models.CueCard
import com.typosbro.multilevel.ui.component.RecognitionControls
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider
import com.typosbro.multilevel.ui.viewmodels.ExamPart
import com.typosbro.multilevel.ui.viewmodels.ExamUiState
import com.typosbro.multilevel.ui.viewmodels.ExamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    onNavigateToResults: (resultId: String) -> Unit,
    viewModel: ExamViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.finalResultId) {
        uiState.finalResultId?.let { onNavigateToResults(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("IELTS Speaking Test") }) }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = uiState.currentPart,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { targetPart ->
                when (targetPart) {
                    ExamPart.NOT_STARTED -> NotStartedView(onStart = { viewModel.startExam() })
                    ExamPart.PART_1, ExamPart.PART_3 -> ExaminerInteractionView(uiState, viewModel)
                    ExamPart.PART_2_PREP -> Part2PrepView(uiState)
                    ExamPart.PART_2_SPEAKING -> Part2SpeakingView(uiState, viewModel)
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
fun ExaminerInteractionView(uiState: ExamUiState, viewModel: ExamViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                onStartRecording = { viewModel.startUserSpeechRecognition() },
                onStopRecording = { viewModel.stopUserSpeechRecognition() }
            )
        }
    }
}

@Composable
fun Part2PrepView(uiState: ExamUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
fun Part2SpeakingView(uiState: ExamUiState, viewModel: ExamViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                onStartRecording = { viewModel.startUserSpeechRecognition() },
                onStopRecording = { viewModel.stopUserSpeechRecognition() }
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