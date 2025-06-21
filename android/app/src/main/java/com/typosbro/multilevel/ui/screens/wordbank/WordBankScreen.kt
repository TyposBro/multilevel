package com.typosbro.multilevel.ui.screens.wordbank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBankScreen(
    onNavigateToReview: () -> Unit,
    onNavigateToDiscovery: () -> Unit, // New navigation action
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val dueWordsCount by viewModel.dueWordsCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Word Bank") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Welcome to your Word Bank!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))

            Card {
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
                    .padding(horizontal = 16.dp)
                    .height(50.dp)
            ) {
                Text(if (dueWordsCount > 0) "Start Review ($dueWordsCount)" else "No Words to Review")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onNavigateToDiscovery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp)
            ) {
                Text("Explore New Words")
            }
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