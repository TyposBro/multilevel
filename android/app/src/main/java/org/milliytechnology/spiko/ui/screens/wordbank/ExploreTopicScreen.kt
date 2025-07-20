// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/screens/wordbank/ExploreTopicScreen.kt
package org.milliytechnology.spiko.ui.screens.wordbank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTopicScreen(
    level: String,
    onNavigateBack: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(level) {
        viewModel.fetchExploreTopics(level)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.topics_for_level, level)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading && uiState.exploreTopics.isEmpty() -> {
                    CircularProgressIndicator()
                }

                uiState.exploreTopics.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_new_topics_found, level),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.exploreTopics, key = { it }) { topic ->
                            ListItem(
                                modifier = Modifier.clip(MaterialTheme.shapes.medium),
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                headlineContent = {
                                    Text(
                                        text = topic,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                trailingContent = {
                                    val loadingKey = "${level}_$topic"
                                    val isLoadingItem = loadingKey in uiState.loadingItems
                                    val isAdded = uiState.exploreTopicAddedStatus[topic] == true

                                    if (isAdded) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.removeWordsByTopic(
                                                    level,
                                                    topic
                                                )
                                            },
                                            enabled = !isLoadingItem
                                        ) {
                                            if (isLoadingItem) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text(stringResource(R.string.action_remove))
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.addWordsByTopic(level, topic) },
                                            enabled = !isLoadingItem
                                        ) {
                                            if (isLoadingItem) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text(stringResource(R.string.action_add))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}