package com.typosbro.multilevel.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.typosbro.multilevel.ui.component.ChatMessage
import com.typosbro.multilevel.ui.component.ChatMessageBubble
import com.typosbro.multilevel.ui.component.RecognitionControls
import com.typosbro.multilevel.ui.component.TimerModal

@Composable
fun VoiceRecognitionScreen(
    messageList: List<String>,
    partialText: String,
    onStartMicRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    isRecording: Boolean
) {
    val listState = rememberLazyListState()
    var showTimerModal by remember { mutableStateOf(false) }

    // Scroll to top when new messages are added
    LaunchedEffect(messageList.size, partialText) {
        if (messageList.isNotEmpty() || partialText.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            reverseLayout = true
        ) {
            // Show current partial results at the top if any
            if (partialText.isNotEmpty()) {
                item {
                    ChatMessageBubble(
                        message = ChatMessage(
                            text = partialText,
                            isUser = true
                        )
                    )
                }
            }

            // Show all completed messages
            items(messageList) { message ->
                ChatMessageBubble(
                    message = ChatMessage(
                        text = message,
                        isUser = true
                    )
                )
            }

            // If there's nothing to show, display an instruction
            if (messageList.isEmpty() && partialText.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tap the microphone button to start recognition")
                    }
                }
            }
        }

        RecognitionControls(
            isRecording = isRecording,
            onStartRecording = { showTimerModal = true },
            onStopRecording = onStopRecognition,
            modifier = Modifier.fillMaxWidth()
        )

        if (showTimerModal) {
            TimerModal(
                timer1Duration = 5,  // Adjust durations as needed.
                timer2Duration = 10,
                onFinish = {
                    // When timer 2 finishes, call stop recording and hide the modal.
                    onStopRecognition()
                    showTimerModal = false
                },
                callback = onStartMicRecognition,
                onCancel = {
                    // Allow user to cancel the timer early.
                    onStopRecognition()
                    showTimerModal = false
                }
            )
        }
    }
}
