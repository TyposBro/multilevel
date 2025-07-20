package org.milliytechnology.spiko.ui.screens.wordbank

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var expandedLevels by remember { mutableStateOf(setOf<String>()) }

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
        topBar = { TopAppBar(title = { Text("Decks") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            StatsBar(
                due = uiState.totalDue,
                new = uiState.totalNew,
                total = uiState.totalWords
            )

            // This Box will now correctly contain either the loader or the weighted list.
            Box(modifier = Modifier.weight(1f)) { // --- FIX 1: Give this Box the weight ---
                if (uiState.isLoading && uiState.deckHierarchy.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // --- FIX 2: Remove fillMaxSize() from here, the weight modifier handles it ---
                    LazyColumn {
                        items(uiState.deckHierarchy, key = { it.name }) { levelDeck ->
                            Column {
                                DeckItemRow(
                                    deck = levelDeck,
                                    isExpanded = levelDeck.name in expandedLevels,
                                    onExpandToggle = { isNowExpanded ->
                                        expandedLevels = if (isNowExpanded) {
                                            expandedLevels + levelDeck.name
                                        } else {
                                            expandedLevels - levelDeck.name
                                        }
                                    },
                                    onReviewClick = {
                                        if (levelDeck.dueCount > 0) {
                                            viewModel.startReviewSession(level = levelDeck.level)
                                            onNavigateToReview()
                                        }
                                    }
                                )

                                AnimatedVisibility(visible = levelDeck.name in expandedLevels) {
                                    Column {
                                        levelDeck.subDecks.forEach { topicDeck ->
                                            DeckItemRow(
                                                deck = topicDeck,
                                                isSubItem = true,
                                                onReviewClick = {
                                                    if (topicDeck.dueCount > 0) {
                                                        viewModel.startReviewSession(
                                                            level = topicDeck.level,
                                                            topic = topicDeck.topic
                                                        )
                                                        onNavigateToReview()
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
            }

            // --- FIX 3: Remove the Spacer. The weight on the Box above handles this now. ---

            // This button will now correctly appear at the bottom.
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

// StatsBar, DeckItemRow, and CountBadge composables remain unchanged.
@Composable
private fun StatsBar(due: Int, new: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Due: ", fontWeight = FontWeight.Bold)
        Text(text = "$due", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Text(text = "New: ", fontWeight = FontWeight.Bold)
        Text(text = "$new", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Text(text = "Total: ", fontWeight = FontWeight.Bold)
        Text(text = "$total", color = Color(0xFFFFA726), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeckItemRow(
    deck: DeckInfo,
    isExpanded: Boolean = false,
    isSubItem: Boolean = false,
    onExpandToggle: ((Boolean) -> Unit)? = null,
    onReviewClick: () -> Unit
) {
    val isClickable = deck.dueCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable, onClick = onReviewClick)
            .padding(
                start = if (isSubItem) 32.dp else 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onExpandToggle != null) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.clickable { onExpandToggle(!isExpanded) }
            )
        } else {
            Spacer(Modifier.width(24.dp))
        }

        Text(
            text = deck.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (isClickable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.6f
            )
        )

        CountBadge(count = deck.dueCount, color = Color(0xFF4CAF50))
        CountBadge(count = deck.newCount, color = Color(0xFF42A5F5))
        CountBadge(count = deck.totalCount, color = Color(0xFFFFA726))
    }
}

@Composable
private fun CountBadge(count: Int, color: Color) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (count > 0) 1f else 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}