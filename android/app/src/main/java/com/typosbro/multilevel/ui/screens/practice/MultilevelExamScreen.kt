package com.typosbro.multilevel.ui.screens.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.typosbro.multilevel.data.remote.models.Part3Topic
import com.typosbro.multilevel.ui.viewmodels.MultilevelExamStage
import com.typosbro.multilevel.ui.viewmodels.MultilevelExamViewModel
import com.typosbro.multilevel.ui.viewmodels.MultilevelUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultilevelExamScreen(
    onNavigateToResults: (resultId: String) -> Unit,
    viewModel: MultilevelExamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // --- PERMISSION HANDLING ---
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasAudioPermission = isGranted }
    )
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // --- NAVIGATION LOGIC ---
    LaunchedEffect(uiState.finalResultId) {
        uiState.finalResultId?.let { onNavigateToResults(it) }
    }

    // --- UI ---
    Scaffold(
        topBar = { TopAppBar(title = { Text("Multilevel Speaking Test") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = uiState.stage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ExamStageAnimation"
            ) { targetStage ->
                when (targetStage) {
                    MultilevelExamStage.NOT_STARTED -> ExamStartView(onStart = {
                        if (hasAudioPermission) viewModel.startExam()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    })

                    MultilevelExamStage.LOADING -> LoadingView("Downloading exam content...")
                    MultilevelExamStage.INTRO,
                    MultilevelExamStage.PART1_2_INTRO,
                    MultilevelExamStage.PART2_INTRO,
                    MultilevelExamStage.PART3_INTRO -> InstructionView(uiState)

                    MultilevelExamStage.PART1_1_QUESTION -> Part1_1_View(uiState)
                    MultilevelExamStage.PART1_2_COMPARE,
                    MultilevelExamStage.PART1_2_FOLLOWUP -> Part1_2_View(uiState)

                    MultilevelExamStage.PART2_PREP,
                    MultilevelExamStage.PART2_SPEAKING -> Part2_View(uiState)

                    MultilevelExamStage.PART3_PREP,
                    MultilevelExamStage.PART3_SPEAKING -> Part3_View(uiState)

                    MultilevelExamStage.ANALYZING -> LoadingView("Analyzing your performance...")
                    MultilevelExamStage.FINISHED_ERROR -> ErrorView(
                        uiState.error ?: "An unknown error occurred."
                    )
                }
            }
        }
    }
}

// --- Specific Views for each Exam Stage ---

@Composable
fun ExamStartView(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Text(
            "Multilevel Speaking Practice",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "This test has 3 parts and will take approximately 15 minutes to complete. Please ensure you are in a quiet environment.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart) {
            Text("Start Exam")
        }
    }
}

@Composable
fun LoadingView(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorView(error: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Text(
            "An Error Occurred",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(error, textAlign = TextAlign.Center)
    }
}


@Composable
fun InstructionView(uiState: MultilevelUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Playing instructions...",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Part1_1_View(uiState: MultilevelUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Text("Part 1: Personal Questions", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(32.dp))
        Text(
            uiState.currentQuestionText ?: "...",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        TimerAndRecordingIndicator(uiState)
    }
}

@Composable
fun Part1_2_View(uiState: MultilevelUiState) {
    val content = uiState.examContent?.part1_2
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Text("Part 1: Picture Comparison", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = content?.image1Url,
                contentDescription = "Image 1",
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            AsyncImage(
                model = content?.image2Url,
                contentDescription = "Image 2",
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            uiState.currentQuestionText ?: "...",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TimerAndRecordingIndicator(uiState)
    }
}

@Composable
fun Part2_View(uiState: MultilevelUiState) {
    val content = uiState.examContent?.part2
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Part 2: Monologue", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        AsyncImage(
            model = content?.imageUrl,
            contentDescription = "Part 2 Image",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(24.dp))
        Text(
            uiState.currentQuestionText ?: "...",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        TimerAndRecordingIndicator(uiState)
    }
}

@Composable
fun Part3_View(uiState: MultilevelUiState) {
    val content = uiState.examContent?.part3
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Part 3: Argument", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (content != null) {
            Part3CueCard(content)
        }
        Spacer(Modifier.height(24.dp))
        TimerAndRecordingIndicator(uiState)
    }
}

@Composable
fun Part3CueCard(topic: Part3Topic) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                topic.topic,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "FOR",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    topic.forPoints.forEach {
                        Text(
                            "• $it",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "AGAINST",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    topic.againstPoints.forEach {
                        Text(
                            "• $it",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun TimerAndRecordingIndicator(uiState: MultilevelUiState) {
    val stage = uiState.stage
    val isPrep = stage == MultilevelExamStage.PART2_PREP || stage == MultilevelExamStage.PART3_PREP

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (uiState.timerValue > 0) {
            Text(
                if (isPrep) "Preparation Time" else "Time Remaining",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                formatTime(uiState.timerValue),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (uiState.timerValue <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(16.dp))
        if (uiState.isRecording) {
            Text(
                "RECORDING",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}