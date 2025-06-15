// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/component/ChatComponent.kt


package com.typosbro.multilevel.ui.component


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.UUID

/**
 * Data class to represent a chat message
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)


@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}


/**
 * UI component for the recognition controls with animated recording indicator
 */

@Composable
fun RecognitionControls(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true // Add the enabled parameter
) {
    // Animate between the start and stop buttons
    AnimatedContent(
        targetState = isRecording,
        transitionSpec = {
            scaleIn() togetherWith scaleOut()
        },
        label = "RecognitionControlButton"
    ) { recording ->
        if (recording) {
            // Stop Button
            FilledIconButton(
                onClick = onStopRecording,
                modifier = modifier.size(64.dp),
                // The button is only clickable if the parent says it's enabled.
                enabled = enabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Recording",
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            // Start Button
            FilledIconButton(
                onClick = onStartRecording,
                modifier = modifier.size(64.dp),
                // The button is only clickable if the parent says it's enabled.
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start Recording",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}