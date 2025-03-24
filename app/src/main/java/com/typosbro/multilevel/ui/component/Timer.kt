package com.typosbro.multilevel.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * TimerModal shows two sequential timers.
 * - The first timer is a popup modal.
 * - The second timer is displayed inline (as a Card) so it doesn't obscure the chat.
 *
 * @param timer1Duration Duration (in seconds) for the first timer.
 * @param timer2Duration Duration (in seconds) for the second timer.
 * @param onFinish Called when both timers have finished (e.g. to stop recording).
 * @param onCancel Called if the user cancels the timer (optional).
 * @param callback Called when timer 1 finishes (e.g. to start recognition).
 */
@Composable
fun TimerModal(
    timer1Duration: Int = 5,
    timer2Duration: Int = 10,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    callback: () -> Unit
) {
    // currentTimer holds the remaining seconds for the active timer.
    var currentTimer by remember { mutableStateOf(timer1Duration) }
    // currentStage: 1 = popup modal timer, 2 = inline timer.
    var currentStage by remember { mutableStateOf(1) }

    // Timer countdown effect.
    LaunchedEffect(currentStage) {
        while (currentTimer > 0) {
            delay(1000L)
            currentTimer--
        }
        if (currentStage == 1) {
            // Timer 1 finished: switch to stage 2.
            currentStage = 2
            currentTimer = timer2Duration
            callback()
        } else {
            // Timer 2 finished: trigger finish.
            onFinish()
        }
    }

    if (currentStage == 1) {
        // Popup modal for Timer 1.
        AlertDialog(
            onDismissRequest = { /* Prevent dismissing on outside touch */ },
            title = { Text("Timer Stage $currentStage") },
            text = { Text("Time remaining: $currentTimer seconds") },
            confirmButton = {
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        )
    } else {
        // Inline UI for Timer 2.
        // This Card will be rendered within the screen layout.
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Timer Stage $currentStage")
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Time remaining: $currentTimer seconds")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}
