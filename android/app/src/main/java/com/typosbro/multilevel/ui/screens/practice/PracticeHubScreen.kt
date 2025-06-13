package com.typosbro.multilevel.ui.screens.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider
import com.typosbro.multilevel.ui.viewmodels.ChatListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeHubScreen(
    onNavigateToExam: () -> Unit,
    // You can keep the old chat list here as "Freestyle Practice"
    onNavigateToChat: (chatId: String) -> Unit,
    viewModel: ChatListViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Speaking Practice") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick = onNavigateToExam // Navigate to the full exam
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Full Mock Exam", style = MaterialTheme.typography.headlineSmall)
                    Text("Simulate a full 11-14 minute test.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Add buttons for individual part practice later
            // ...

            HorizontalDivider(modifier = Modifier.padding(16.dp))
            Text("Freestyle Chat History", style = MaterialTheme.typography.titleMedium)
            // Your existing LazyColumn with the chat list goes here
        }
    }
}