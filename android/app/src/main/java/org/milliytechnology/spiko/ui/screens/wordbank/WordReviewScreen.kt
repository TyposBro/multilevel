package org.milliytechnology.spiko.ui.screens.wordbank

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexstyl.swipeablecard.ExperimentalSwipeableCardApi
import com.typosbro.multilevel.data.local.WordEntity
import com.typosbro.multilevel.features.srs.ReviewQuality
import org.milliytechnology.spiko.ui.viewmodels.WordBankViewModel
import kotlinx.coroutines.launch

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
            } else if (uiState.isSessionActive && currentWord == null) {
                CircularProgressIndicator()
            } else if (uiState.isSessionActive && currentWord != null) {
                val scope = rememberCoroutineScope()

                fun onReview(quality: ReviewQuality) {
                    viewModel.handleReview(currentWord, quality)
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        20.dp,
                        alignment = Alignment.CenterVertically
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        WordReviewCard(
                            word = currentWord,
                        )
                    }

                    // --- NEW: 2x2 Grid Layout for Buttons ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Row: Again, Hard
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ReviewButton(
                                text = "Again",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            ) {
                                scope.launch { onReview(ReviewQuality.AGAIN) }
                            }
                            ReviewButton(
                                text = "Hard",
                                color = Color(0xFFFFA726),
                                modifier = Modifier.weight(1f)
                            ) {
                                scope.launch { onReview(ReviewQuality.HARD) }
                            }
                        }
                        // Bottom Row: Good, Easy
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ReviewButton(
                                text = "Good",
                                color = Color(0xFF42A5F5),
                                modifier = Modifier.weight(1f)
                            ) {
                                scope.launch { onReview(ReviewQuality.GOOD) }
                            }
                            ReviewButton(
                                text = "Easy",
                                color = Color(0xFF66BB6A),
                                modifier = Modifier.weight(1f)
                            ) {
                                scope.launch { onReview(ReviewQuality.EASY) }
                            }
                        }
                    }
                }
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

// --- UPDATED: Helper for larger, more readable buttons ---
@Composable
private fun ReviewButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp), // Increased height for better tap target
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge, // Larger font
            fontWeight = FontWeight.Bold // Bolder font
        )
    }
}


@OptIn(ExperimentalSwipeableCardApi::class)
@Composable
fun WordReviewCard(
    word: WordEntity,
) {
    var isRevealed by remember { mutableStateOf(false) }

    // When a new word is displayed, reset the revealed state
    LaunchedEffect(word) {
        isRevealed = false
    }

    Box(
        Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(3f / 4f)
    ) {
        // Our new FlippableCard handles the rotation and content switching
        FlippableCard(
            isFlipped = isRevealed,
            onClick = { isRevealed = !isRevealed },
            front = {
                // Content for the front of the card
                CardContent(
                    title = word.word,
                    titleStyle = MaterialTheme.typography.displayMedium,
                    example = word.example1,

                    )
            },
            back = {
                // Content for the back of the card
                CardContent(
                    title = word.translation,
                    titleStyle = MaterialTheme.typography.headlineMedium,
                    titleColor = MaterialTheme.colorScheme.primary,
                    example = word.example1Translation
                )
            }
        )
    }
}

/**
 * A reusable composable that shows a card with a front and back that can be flipped.
 */
@Composable
fun FlippableCard(
    isFlipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    // Animate the rotationY value between 0f (front) and 180f (back)
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "cardFlipRotation"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            // Apply the animated rotation to the graphics layer
            .graphicsLayer {
                rotationY = rotation
                // Add a perspective effect
                cameraDistance = 12 * density
            }
    ) {
        // Show the back of the card if the rotation is past 90 degrees, otherwise show the front.
        // The back is also rotated so it's not mirrored.
        if (rotation <= 90f) {
            Box(Modifier.fillMaxSize()) {
                front()
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    // Rotate the back content so it's facing the correct way
                    .graphicsLayer { rotationY = 180f }
            ) {
                back()
            }
        }
    }
}

/**
 * A generic composable for displaying content inside the review card.
 * Used for both the front and back sides.
 */
@Composable
private fun CardContent(
    title: String,
    titleStyle: TextStyle,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    example: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = titleColor,
            textAlign = TextAlign.Center
        )

        if (example != null) {
            Spacer(Modifier.height(32.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "e.g. $example",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
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