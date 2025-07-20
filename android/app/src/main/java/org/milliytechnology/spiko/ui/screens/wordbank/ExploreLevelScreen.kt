// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/screens/wordbank/ExploreLevelScreen.kt
package org.milliytechnology.spiko.ui.screens.wordbank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // <-- THE FIX IS HERE
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.milliytechnology.spiko.ui.viewmodels.WordBankUiState
import org.milliytechnology.spiko.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreLevelScreen(
    onLevelSelected: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // M3: A scroll behavior allows the TopAppBar to react to user scrolling.
    // `enterAlwaysScrollBehavior` makes the bar reappear as soon as the user scrolls up.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        viewModel.fetchExploreLevels()
    }

    Scaffold(
        // M3: Connect the scroll behavior to the Scaffold.
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Explore Levels") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back" // Recommended: Use stringResource
                        )
                    }
                },
                // M3: Apply the scroll behavior to the TopAppBar.
                // It will now change its container color automatically when scrolled.
                scrollBehavior = scrollBehavior,
                // M3: Using TopAppBarDefaults ensures the colors are sourced
                // directly from your AppTheme's colorScheme.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        // M3: Use a `when` block to handle all UI states explicitly for a more robust UX.
        when {
            // 1. Initial Loading State
            uiState.isLoading && uiState.exploreLevels.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // 2. Empty State (after loading)
            !uiState.isLoading && uiState.exploreLevels.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp), // Add some horizontal padding for the text
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No new levels found. Check back later!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 3. Content State
            else -> {
                ExploreLevelList(
                    uiState = uiState,
                    paddingValues = paddingValues,
                    onLevelSelected = onLevelSelected,
                    onAddLevel = viewModel::addWordsByLevel,
                    onRemoveLevel = viewModel::removeWordsByLevel
                )
            }
        }
    }
}

@Composable
private fun ExploreLevelList(
    uiState: WordBankUiState,
    paddingValues: PaddingValues,
    onLevelSelected: (String) -> Unit,
    onAddLevel: (String) -> Unit,
    onRemoveLevel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // M3: Apply the Scaffold's padding to the LazyColumn's contentPadding.
    // This prevents content from being hidden behind the top/bottom bars.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = paddingValues
    ) {
        items(items = uiState.exploreLevels, key = { it }) { level ->
            val isLoadingItem = level in uiState.loadingItems
            val isAdded = uiState.exploreLevelsAddedStatus[level] == true

            ListItem(
                headlineContent = {
                    Text(
                        text = level,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                modifier = Modifier.clickable { onLevelSelected(level) },
                // M3: ListItem comes with default colors, but you can override them.
                // Using transparent ensures it perfectly blends with the Scaffold's background.
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                trailingContent = {
                    LevelActionButton(
                        isAdded = isAdded,
                        isLoading = isLoadingItem,
                        onAddClick = { onAddLevel(level) },
                        onRemoveClick = { onRemoveLevel(level) }
                    )
                }
            )
            // M3: Using a Divider with horizontal padding gives it a nice inset look,
            // aligning it with the list item content instead of the screen edges.
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun LevelActionButton(
    isAdded: Boolean,
    isLoading: Boolean,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    if (isAdded) {
        OutlinedButton(
            onClick = onRemoveClick,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    // M3: Progress indicator automatically uses the 'primary' color,
                    // which is appropriate for an OutlinedButton.
                )
            } else {
                Text("Remove")
            }
        }
    } else {
        Button(
            onClick = onAddClick,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    // M3: Explicitly set the color to contrast with the button's
                    // primary background. `onPrimary` is the correct choice.
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Add")
            }
        }
    }
}