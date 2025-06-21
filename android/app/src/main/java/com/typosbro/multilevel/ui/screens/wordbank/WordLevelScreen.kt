// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/wordbank/WordLevelScreen.kt
package com.typosbro.multilevel.ui.screens.wordbank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordLevelScreen(
    onLevelSelected: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        // Fetch levels when the screen is first composed
        viewModel.fetchLevels()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a Level") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.levels) { level ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = level,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            modifier = Modifier.clickable { onLevelSelected(level) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}