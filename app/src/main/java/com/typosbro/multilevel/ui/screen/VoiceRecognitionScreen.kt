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
import org.vosk.Model

@Composable
fun VoiceRecognitionScreen(
    model: Model?,
    recognitionResults: String,
    partialResults: String,
    onStartMicRecognition: () -> Unit,
    onStartFileRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    onPauseStateChange: (Boolean) -> Unit,
    isPaused: Boolean
) {
    val listState = rememberLazyListState()

    // Keep track of completed messages
    val completedMessages = remember { mutableStateListOf<String>() }

    // Add current recognition results to completed messages when recording stops
    LaunchedEffect(recognitionResults) {
        if (recognitionResults.isNotEmpty() && partialResults.isEmpty()) {
            // Only add if it's not already in the list and is not empty
            val trimmedResults = recognitionResults.trim()
            if (trimmedResults.isNotEmpty() && !completedMessages.contains(trimmedResults)) {
                completedMessages.add(0, trimmedResults)
            }
        }
    }

    // When recording stops (and we have partial results), add the partial as a completed message
    val isRecording = partialResults.isNotEmpty()
    var wasRecording by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (!isRecording && wasRecording && partialResults.isNotEmpty()) {
            // Recording just stopped, save the current partial results
            val trimmedPartial = partialResults.trim()
            if (trimmedPartial.isNotEmpty() && !completedMessages.contains(trimmedPartial)) {
                completedMessages.add(0, trimmedPartial)
            }
        }
        wasRecording = isRecording
    }

    // Scroll to top when new messages are added
    LaunchedEffect(completedMessages.size, partialResults) {
        if (completedMessages.isNotEmpty() || partialResults.isNotEmpty()) {
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
            if (partialResults.isNotEmpty()) {
                item {
                    ChatMessageBubble(
                        message = ChatMessage(
                            text = partialResults,
                            isUser = false
                        )
                    )
                }
            }

            // Show all completed messages
            items(completedMessages) { message ->
                ChatMessageBubble(
                    message = ChatMessage(
                        text = message,
                        isUser = false
                    )
                )
            }

            // If there's nothing to show, display an instruction
            if (completedMessages.isEmpty() && partialResults.isEmpty()) {
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

        // Custom wrapper for controls that handles our custom logic
        val customStopRecognition = {
            // We don't need any special handling here now since LaunchedEffect
            // will detect when recording stops
            onStopRecognition()
        }

        RecognitionControls(
            isRecognizing = partialResults.isNotEmpty(),
            isPaused = isPaused,
            onStartRecognition = onStartMicRecognition,
            onStopRecognition = customStopRecognition,
            onPauseStateChange = onPauseStateChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}