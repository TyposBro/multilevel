package com.typosbro.multilevel.ui.screens.wordbank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTopicScreen(
    level: String,
    onNavigateBack: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(level) {
        viewModel.fetchExploreTopics(level)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Topics for $level") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading && uiState.exploreTopics.isEmpty()) {
                CircularProgressIndicator()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.exploreTopics, key = { it }) { topic ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    topic,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            trailingContent = {
                                val loadingKey = "${level}_$topic"
                                val isLoadingItem = loadingKey in uiState.loadingItems
                                val isAdded = uiState.exploreTopicAddedStatus[topic] == true

                                // --- REVISED: Show a different button based on 'isAdded' status ---
                                if (isAdded) {
                                    // Show a "Remove" button if the topic is already added
                                    OutlinedButton(
                                        onClick = { viewModel.removeWordsByTopic(level, topic) },
                                        enabled = !isLoadingItem
                                    ) {
                                        if (isLoadingItem) {
                                            CircularProgressIndicator(Modifier.size(24.dp))
                                        } else {
                                            Text("Remove")
                                        }
                                    }
                                } else {
                                    // Show an "Add" button if the topic is not added
                                    Button(
                                        onClick = { viewModel.addWordsByTopic(level, topic) },
                                        enabled = !isLoadingItem
                                    ) {
                                        if (isLoadingItem) {
                                            CircularProgressIndicator(
                                                Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Add")
                                        }
                                    }
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}