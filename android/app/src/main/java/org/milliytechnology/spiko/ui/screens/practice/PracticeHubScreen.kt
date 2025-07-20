package org.milliytechnology.spiko.ui.screens.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Enum to define the practice parts for type safety
enum class PracticePart(val title: String, val description: String) {
    FULL("Full Mock Exam", "A complete 3-part speaking simulation."),
    P1_1("Part 1.1 Practice", "Answer personal questions."),
    P1_2("Part 1.2 Practice", "Compare and contrast two pictures."),
    P2("Part 2 Practice", "Deliver a monologue based on a cue card."),
    P3("Part 3 Practice", "Discuss and argue a given topic.")
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeHubScreen(
    onPartSelected: (PracticePart) -> Unit,
) {
    Scaffold { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PracticePart.entries.forEach { part ->
                PracticePartCard(
                    title = part.title,
                    description = part.description,
                    onClick = { onPartSelected(part) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticePartCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}