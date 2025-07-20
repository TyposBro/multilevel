// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/screens/wordbank/WordBankScreen.kt
package org.milliytechnology.spiko.ui.screens.wordbank

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.milliytechnology.spiko.ui.viewmodels.DeckInfo
import org.milliytechnology.spiko.ui.viewmodels.WordBankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBankScreen(
    onNavigateToReview: () -> Unit,
    onNavigateToExplore: () -> Unit,
    viewModel: WordBankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // Fetches data when the screen is resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                viewModel.loadDeckHierarchy()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Decks") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // M3: Redesigned stats bar for a cleaner look
            StatsBar(
                due = uiState.totalDue,
                new = uiState.totalNew,
                total = uiState.totalWords
            )

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading && uiState.deckHierarchy.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (!uiState.isLoading && uiState.deckHierarchy.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Your word bank is empty.\nExplore new words to get started!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    DeckList(
                        decks = uiState.deckHierarchy,
                        onNavigateToReview = onNavigateToReview,
                        onStartReview = viewModel::startReviewSession,
                    )
                }
            }

            OutlinedButton(
                onClick = onNavigateToExplore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Explore New Words")
            }
        }
    }
}

@Composable
private fun DeckList(
    decks: List<DeckInfo>,
    onNavigateToReview: () -> Unit,
    onStartReview: (String?, String?) -> Unit
) {
    var expandedLevels by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        items(decks, key = { it.name }) { levelDeck ->
            val isExpanded = levelDeck.name in expandedLevels
            DeckItemRow(
                deck = levelDeck,
                isExpanded = isExpanded,
                onExpandToggle = {
                    expandedLevels = if (isExpanded) {
                        expandedLevels - levelDeck.name
                    } else {
                        expandedLevels + levelDeck.name
                    }
                },
                onReviewClick = {
                    onStartReview(levelDeck.level, null)
                    onNavigateToReview()
                }
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    levelDeck.subDecks.forEach { topicDeck ->
                        DeckItemRow(
                            deck = topicDeck,
                            isSubItem = true,
                            onReviewClick = {
                                onStartReview(topicDeck.level, topicDeck.topic)
                                onNavigateToReview()
                            }
                        )
                        Divider(modifier = Modifier.padding(start = 48.dp, end = 16.dp))
                    }
                }
            }
        }
    }
}

// M3: Redesigned stats bar to be cleaner and use theme colors.
@Composable
private fun StatsBar(due: Int, new: Int, total: Int) {
    Surface(
        // Uses a subtle container color from the theme for visual separation.
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            StatItem(count = due, label = "Due", color = MaterialTheme.colorScheme.tertiary)
            StatItem(count = new, label = "New", color = MaterialTheme.colorScheme.primary)
            StatItem(
                count = total,
                label = "Total",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// M3: Replaced the custom row with a standard ListItem for consistency.
@Composable
private fun DeckItemRow(
    deck: DeckInfo,
    isExpanded: Boolean = false,
    isSubItem: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
    onReviewClick: () -> Unit
) {
    val isClickable = deck.dueCount > 0

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable, onClick = onReviewClick)
            // Indent sub-items for clear hierarchy
            .padding(start = if (isSubItem) 16.dp else 0.dp),
        colors = ListItemDefaults.colors(
            // Use transparent to blend with the scaffold background
            containerColor = Color.Transparent,
            // Automatically uses appropriate colors for disabled state
            headlineColor = if (isClickable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.6f
            )
        ),
        headlineContent = { Text(text = deck.name) },
        leadingContent = {
            if (onExpandToggle != null) {
                // The expand/collapse icon is now the leading content.
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                    )
                }
            }
        },
        trailingContent = {
            // The count badges are now the trailing content.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CountBadge(
                    count = deck.dueCount,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                CountBadge(
                    count = deck.newCount,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    )
}

// M3: CountBadge now uses container/content color pairs from the theme.
@Composable
private fun CountBadge(
    count: Int,
    containerColor: Color,
    contentColor: Color
) {
    // Determine colors based on whether the count is zero (inactive)
    val currentContainerColor =
        if (count > 0) containerColor else MaterialTheme.colorScheme.surfaceVariant
    val currentContentColor =
        if (count > 0) contentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(currentContainerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = currentContentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}