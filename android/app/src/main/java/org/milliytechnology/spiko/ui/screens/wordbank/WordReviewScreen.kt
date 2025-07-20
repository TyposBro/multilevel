// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/screens/wordbank/WordReviewScreen.kt
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.data.local.WordEntity
import org.milliytechnology.spiko.features.srs.ReviewQuality
import org.milliytechnology.spiko.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentWord = uiState.currentWord
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()


    LaunchedEffect(Unit) {
        viewModel.startReviewSession()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_session_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isSessionFinished -> {
                    SessionComplete(onNavigateBack)
                }

                uiState.isLoading && currentWord == null -> {
                    CircularProgressIndicator()
                }

                uiState.isSessionActive && currentWord != null -> {
                    Reviewer(
                        currentWord = currentWord,
                        onReview = { quality ->
                            viewModel.handleReview(currentWord, quality)
                        }
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.no_words_to_review),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Reviewer(
    currentWord: WordEntity,
    onReview: (ReviewQuality) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, alignment = Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            WordReviewCard(word = currentWord)
        }

        // --- UPDATED: 2x2 Grid Layout for M3 Themed Buttons ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val buttonModifier = Modifier
                .height(56.dp)
                .weight(1f)
            // Top Row: Again, Hard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onReview(ReviewQuality.AGAIN) },
                    modifier = buttonModifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.review_again),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = { onReview(ReviewQuality.HARD) },
                    modifier = buttonModifier
                ) {
                    Text(
                        text = stringResource(R.string.review_hard),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Bottom Row: Good, Easy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilledTonalButton(
                    onClick = { onReview(ReviewQuality.GOOD) },
                    modifier = buttonModifier
                ) {
                    Text(
                        text = stringResource(R.string.review_good),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onReview(ReviewQuality.EASY) },
                    modifier = buttonModifier
                ) {
                    Text(
                        text = stringResource(R.string.review_easy),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


@Composable
fun WordReviewCard(word: WordEntity) {
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
        FlippableCard(
            isFlipped = isRevealed,
            onClick = { isRevealed = !isRevealed },
            front = {
                CardContent(
                    title = word.word,
                    titleStyle = MaterialTheme.typography.displayMedium,
                    example = word.example1,
                )
            },
            back = {
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


@Composable
fun FlippableCard(
    isFlipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "cardFlipRotation"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        if (rotation <= 90f) {
            Box(Modifier.fillMaxSize()) {
                front()
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
            ) {
                back()
            }
        }
    }
}


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
            .padding(24.dp), // Increased padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = titleColor,
            textAlign = TextAlign.Center
        )

        if (!example.isNullOrBlank()) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.example_prefix, example),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
        Text(
            text = stringResource(R.string.session_complete_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.session_complete_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateBack) {
            Text(stringResource(R.string.go_back))
        }
    }
}