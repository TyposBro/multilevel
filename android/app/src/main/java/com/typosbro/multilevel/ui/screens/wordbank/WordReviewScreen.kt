// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/wordbank/WordReviewScreen.kt
package com.typosbro.multilevel.ui.screens.wordbank

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.data.local.WordEntity
import com.typosbro.multilevel.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Session") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endReviewSession()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                SessionFinishedView(onNavigateBack)
            } else if (uiState.currentWord != null) {
                AnimatedContent(
                    targetState = uiState.currentWord,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                                fadeOut(animationSpec = tween(90))
                    }
                ) { targetWord ->
                    Flashcard(
                        word = targetWord!!,
                        onKnewIt = { viewModel.handleReview(targetWord, knewIt = true) },
                        onDidNotKnowIt = { viewModel.handleReview(targetWord, knewIt = false) }
                    )
                }
            } else {
                CircularProgressIndicator() // Or a "no words" message if it starts empty
            }
        }
    }
}


@Composable
fun Flashcard(
    word: WordEntity,
    onKnewIt: () -> Unit,
    onDidNotKnowIt: () -> Unit
) {
    var isFlipped by remember(word) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The card itself
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f) // Rectangular card
                .padding(32.dp)
                .clickable { isFlipped = !isFlipped },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!isFlipped) {
                    // Front of the card
                    Text(word.text, style = MaterialTheme.typography.displayMedium)
                } else {
                    // Back of the card
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(word.definition, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Text(word.example, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(
                onClick = onDidNotKnowIt,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Don't Know") }

            Button(
                onClick = onKnewIt,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("I Knew It") }
        }
    }
}

@Composable
fun SessionFinishedView(onNavigateBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("All done for now!", style = MaterialTheme.typography.headlineMedium)
        Text("Come back later for new reviews.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNavigateBack) { Text("Back to Word Bank") }
    }
}