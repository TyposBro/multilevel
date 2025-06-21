package com.typosbro.multilevel.ui.screens.wordbank

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.data.local.WordEntity
import com.typosbro.multilevel.ui.viewmodels.WordBankViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentWord = uiState.currentWord

    LaunchedEffect(Unit) {
        viewModel.startReviewSession()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Session") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isSessionFinished) {
                SessionComplete(onNavigateBack)
            } else if (currentWord != null) {
                SwipeableWordCard(
                    word = currentWord,
                    onSwipe = { knewIt ->
                        viewModel.handleReview(currentWord, knewIt)
                    }
                )
            } else if (uiState.isSessionActive) {
                CircularProgressIndicator() // Loading words
            } else {
                Text(
                    "No words to review right now. Come back later!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableWordCard(
    word: WordEntity,
    onSwipe: (knewIt: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var isRevealed by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp.value

    val rotation = (offsetX.value / screenWidth) * 15f
    val cardAlpha = 1f - (abs(offsetX.value) / screenWidth / 2)

    val swipeThreshold = screenWidth * 0.4f

    Card(
        onClick = { isRevealed = !isRevealed },
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(3f / 4f)
            .graphicsLayer(
                translationX = offsetX.value,
                rotationZ = rotation
            )
            .alpha(cardAlpha)
            .pointerInput(word.word) { // Relaunch when the word changes
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offsetX.targetValue > swipeThreshold -> {
                                    offsetX.animateTo(screenWidth * 1.5f, tween(300))
                                    onSwipe(true) // Knew it
                                }

                                offsetX.targetValue < -swipeThreshold -> {
                                    offsetX.animateTo(-screenWidth * 1.5f, tween(300))
                                    onSwipe(false) // Didn't know
                                }

                                else -> {
                                    offsetX.animateTo(0f, tween(300))
                                }
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.targetValue + dragAmount.x)
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )

            if (isRevealed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = word.translation,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    word.example1?.let {
                        Text(
                            text = "e.g. $it",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        word.example1Translation?.let { trans ->
                            Text(
                                text = trans,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Text(
                text = if (isRevealed) "" else "Tap to reveal",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun SessionComplete(onNavigateBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text("Great job!", style = MaterialTheme.typography.headlineLarge)
        Text("You've finished your review session.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateBack) {
            Text("Go back")
        }
    }
}