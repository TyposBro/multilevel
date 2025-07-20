package org.milliytechnology.spiko.ui.screens.practice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.data.remote.models.Part3Topic
import org.milliytechnology.spiko.ui.component.HandleAppLifecycle
import org.milliytechnology.spiko.ui.component.ImageLoader
import org.milliytechnology.spiko.ui.component.RecognitionControls
import org.milliytechnology.spiko.ui.viewmodels.ExamViewModel
import org.milliytechnology.spiko.ui.viewmodels.MultilevelExamStage
import org.milliytechnology.spiko.ui.viewmodels.MultilevelUiState

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    onNavigateToResults: (resultId: String) -> Unit,
    viewModel: ExamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    HandleAppLifecycle(onStop = viewModel::stopExam)

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
        onResult = { isGranted -> hasAudioPermission = isGranted }
    )
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.finalResultId) {
        uiState.finalResultId?.let { onNavigateToResults(it) }
    }

    val showSpeakingUi = when (uiState.stage) {
        MultilevelExamStage.PART1_1_QUESTION,
        MultilevelExamStage.PART1_2_COMPARE,
        MultilevelExamStage.PART1_2_FOLLOWUP,
        MultilevelExamStage.PART2_SPEAKING,
        MultilevelExamStage.PART3_SPEAKING -> true

        else -> false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(getDynamicHeader(uiState.stage)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimerSection(uiState, isCompact = showSpeakingUi)

            Box(modifier = Modifier.weight(1f)) {
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
                        MultilevelExamStage.INTRO -> InstructionView(stringResource(R.string.PART1_1_INTRO))
                        MultilevelExamStage.PART1_2_INTRO -> InstructionView(stringResource(R.string.PART1_2_INTRO))
                        MultilevelExamStage.PART2_INTRO -> InstructionView(stringResource(R.string.PART2_INTRO))
                        MultilevelExamStage.PART3_INTRO -> InstructionView(stringResource(R.string.PART3_INTRO))
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

            AnimatedVisibility(
                visible = showSpeakingUi,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TranscriptDisplay(
                        currentAnswer = uiState.currentAnswerTranscript,
                        liveTranscript = uiState.liveTranscript,
                        isRecording = uiState.isRecording
                    )
                    RecognitionControls(
                        isRecording = uiState.isRecording,
                        onStartRecording = { /* Disabled */ },
                        onStopRecording = { viewModel.onStopRecordingClicked() },
                        modifier = Modifier.padding(bottom = 8.dp),
                        enabled = uiState.isRecording
                    )
                }
            }
        }
    }
}


@Composable
fun TimerSection(uiState: MultilevelUiState, isCompact: Boolean) {
    val stage = uiState.stage
    val isPrep = stage == MultilevelExamStage.PART2_PREP || stage == MultilevelExamStage.PART3_PREP
    val showTimer = uiState.timerValue > 0 && stage != MultilevelExamStage.NOT_STARTED &&
            stage != MultilevelExamStage.LOADING && stage != MultilevelExamStage.ANALYZING &&
            stage != MultilevelExamStage.FINISHED_ERROR && !stage.name.contains("INTRO")

    val animatedHeight by animateDpAsState(
        targetValue = if (isCompact) 60.dp else 100.dp,
        label = "TimerHeightAnimation"
    )
    val animatedPadding by animateDpAsState(
        targetValue = if (isCompact) 8.dp else 16.dp,
        label = "TimerPaddingAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(animatedHeight),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (showTimer) 1f else 0f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (showTimer) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(animatedPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showTimer) {
                AnimatedVisibility(visible = !isCompact) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isPrep) "Preparation Time" else "Time Remaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formatTime(uiState.timerValue),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (uiState.timerValue <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    if (uiState.isRecording) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "● REC",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExamStartView(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Multilevel Speaking Practice",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "This test has 3 parts and will take approximately 15 minutes to complete.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Please ensure you are in a quiet environment with a good microphone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(
                text = stringResource(id = R.string.notice_english_only),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Start Exam")
        }
    }
}

@Composable
fun LoadingView(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorView(error: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "An Error Occurred",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
                text = error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun InstructionView(instruction: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
fun Part1_1_View(uiState: MultilevelUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Text(
                text = uiState.currentQuestionText ?: "Loading question...",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(28.dp)
            )
        }
    }
}

@Composable
fun Part1_2_View(uiState: MultilevelUiState) {
    val content = uiState.examContent?.part1_2
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImageLoader(
                        imageUrl = content?.image1Url,
                        contentDescription = "Image 1",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    ImageLoader(
                        imageUrl = content?.image2Url,
                        contentDescription = "Image 2",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.currentQuestionText ?: "Loading question...",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun Part2_View(uiState: MultilevelUiState) {
    val content = uiState.examContent?.part2
    val questionText = uiState.currentQuestionText ?: "Loading question..."
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (!content?.imageUrl.isNullOrBlank()) {
                    ImageLoader(
                        imageUrl = content.imageUrl,
                        contentDescription = "Part 2 Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                val bulletPoints = questionText.split("\n").filter { it.isNotBlank() }
                val paragraphStyle = ParagraphStyle(textIndent = TextIndent(restLine = 16.sp))
                bulletPoints.forEach { point ->
                    if (point.trim().isNotEmpty()) {
                        Text(
                            buildAnnotatedString {
                                withStyle(style = paragraphStyle) {
                                    append("•\u00A0\u00A0")
                                    append(point.trim())
                                }
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Update your Part3_View composable to have higher z-index
@Composable
fun Part3_View(uiState: MultilevelUiState) {
    val content = uiState.examContent?.part3

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .zIndex(10f), // Higher z-index than transcript
        contentAlignment = Alignment.TopCenter
    ) {
        if (content != null) {
            Part3CueCard(content)
        }
    }
}

@Composable
fun Part3CueCard(topic: Part3Topic) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = topic.topic,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Text(
                text = "FOR",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            topic.forPoints.forEach { point ->
                CueCardPoint(text = point)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "AGAINST",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
            topic.againstPoints.forEach { point ->
                CueCardPoint(text = point)
            }
        }
    }
}

@Composable
private fun CueCardPoint(text: String) {
    val paragraphStyle = ParagraphStyle(textIndent = TextIndent(restLine = 12.sp))
    Text(
        buildAnnotatedString {
            withStyle(style = paragraphStyle) {
                append("•\u00A0\u00A0")
                append(text)
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun TranscriptDisplay(
    currentAnswer: String,
    liveTranscript: String?,
    isRecording: Boolean
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(currentAnswer, liveTranscript) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .verticalScroll(scrollState)
            .zIndex(-10f)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            if (currentAnswer.isNotBlank()) {
                Text(
                    text = currentAnswer,
                    textAlign = TextAlign.Right,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (isRecording && !liveTranscript.isNullOrBlank()) {
                Text(
                    text = liveTranscript,
                    textAlign = TextAlign.Right,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

@Composable
fun getDynamicHeader(stage: MultilevelExamStage): String {
    return when (stage) {
        MultilevelExamStage.NOT_STARTED -> "Multilevel Speaking Test"
        MultilevelExamStage.LOADING -> "Loading..."
        MultilevelExamStage.INTRO -> "Part 1: Introduction"
        MultilevelExamStage.PART1_1_QUESTION -> "Part 1: Personal Questions"
        MultilevelExamStage.PART1_2_INTRO -> "Part 1: Picture Comparison"
        MultilevelExamStage.PART1_2_COMPARE, MultilevelExamStage.PART1_2_FOLLOWUP -> "Part 1: Picture Comparison"
        MultilevelExamStage.PART2_INTRO -> "Part 2: Monologue"
        MultilevelExamStage.PART2_PREP, MultilevelExamStage.PART2_SPEAKING -> "Part 2: Monologue"
        MultilevelExamStage.PART3_INTRO -> "Part 3: Argument"
        MultilevelExamStage.PART3_PREP, MultilevelExamStage.PART3_SPEAKING -> "Part 3: Argument"
        MultilevelExamStage.ANALYZING -> "Analyzing Results"
        MultilevelExamStage.FINISHED_ERROR -> "Error"
    }
}

// TranscriptOverlay is omitted as it was experimental and you can add it back if needed.
// The Preview is also omitted but can be added back in the same way.