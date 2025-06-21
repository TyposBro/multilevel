// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/component/RecognitionControls.kt
package com.typosbro.multilevel.ui.component


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.typosbro.multilevel.R

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
                    contentDescription = stringResource(id = R.string.recording_stop),
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
                    contentDescription = stringResource(id = R.string.recording_start),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}