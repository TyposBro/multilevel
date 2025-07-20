package org.milliytechnology.spiko.ui.screens.practice

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.milliytechnology.spiko.R

// Enum to define the practice parts for type safety with string resource IDs
enum class PracticePart(
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int
) {
    FULL(R.string.practice_full_title, R.string.practice_full_description),
    P1_1(R.string.practice_p1_1_title, R.string.practice_p1_1_description),
    P1_2(R.string.practice_p1_2_title, R.string.practice_p1_2_description),
    P2(R.string.practice_p2_title, R.string.practice_p2_description),
    P3(R.string.practice_p3_title, R.string.practice_p3_description)
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
                    title = stringResource(part.titleResId),
                    description = stringResource(part.descriptionResId),
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