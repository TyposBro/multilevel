package org.milliytechnology.spiko.ui.screens.practice

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.viewmodels.PracticeHubEvent
import org.milliytechnology.spiko.ui.viewmodels.PracticeHubViewModel

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
    onNavigateToExam: (PracticePart) -> Unit,
    onNavigateToSubscription: () -> Unit,
    viewModel: PracticeHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // This effect listens for one-shot events from the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PracticeHubEvent.NavigateToExam -> onNavigateToExam(event.practicePart)
                is PracticeHubEvent.ShowErrorToast -> {
                    Toast.makeText(context, "Could not check usage limits. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
                    onClick = { viewModel.onPartSelected(part) }
                )
            }
        }
    }

    // This dialog will appear when the ViewModel's state dictates.
    if (uiState.showPaywallDialog) {
        PaywallDialog(
            message = uiState.paywallMessage,
            onDismiss = { viewModel.dismissPaywallDialog() },
            onUpgrade = {
                viewModel.dismissPaywallDialog()
                onNavigateToSubscription() // Navigate to the subscription screen
            }
        )
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

@Composable
private fun PaywallDialog(
    message: String,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.free_limit_reached_title)) },
        text = { Text(text = message) },
        confirmButton = {
            Button(onClick = onUpgrade) {
                Text(text = stringResource(R.string.button_upgrade))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.button_cancel))
            }
        }
    )
}