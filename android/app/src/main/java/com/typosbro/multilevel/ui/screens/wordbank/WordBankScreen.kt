// Create new file: ui/screens/wordbank/WordBankScreen.kt
package com.typosbro.multilevel.ui.screens.wordbank

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider
import com.typosbro.multilevel.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBankScreen(
    // Add navigation to review screen
    onNavigateToReview: () -> Unit,
    viewModel: WordBankViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val dueWordsCount by viewModel.dueWordsCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Word Bank") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Welcome to your Word Bank!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))

            Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem("Words Due", dueWordsCount.toString())
                    // Add more stats later, e.g., "Learning", "Mastered"
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.startReviewSession()
                    onNavigateToReview()
                },
                enabled = dueWordsCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(50.dp)
            ) {
                Text(if (dueWordsCount > 0) "Start Review ($dueWordsCount)" else "No Words to Review")
            }

            // Add "Explore new words" button here later
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}