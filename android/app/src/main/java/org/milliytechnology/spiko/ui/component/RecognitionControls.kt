// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/component/RecognitionControls.kt
package org.milliytechnology.spiko.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import org.milliytechnology.spiko.R

/**
 * A Material 3-styled control button for starting and stopping a recognition/recording process.
 *
 * It uses a FilledTonalIconButton for the "start" state and a more prominent
 * FilledIconButton for the "stop" state, which includes a pulsing animation
 * to indicate that recording is actively in progress.
 *
 * @param isRecording Whether the recording is currently active.
 * @param onStartRecording Callback to invoke when the start button is clicked.
 * @param onStopRecording Callback to invoke when the stop button is clicked.
 * @param modifier The modifier to be applied to the control.
 * @param enabled Controls the enabled state of the button.
 */
@Composable
fun RecognitionControls(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // This transition will run infinitely for the pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    AnimatedContent(
        targetState = isRecording,
        transitionSpec = {
            scaleIn(initialScale = 0.8f) togetherWith scaleOut(targetScale = 0.8f)
        },
        label = "RecognitionControlButton",
        modifier = modifier
    ) { recording ->
        if (recording) {
            // Stop Button: High emphasis, uses primary error color and a pulsing animation.
            FilledIconButton(
                onClick = onStopRecording,
                enabled = enabled,
                // Apply the pulsing scale animation only when recording
                modifier = Modifier.scale(pulseScale),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(id = R.string.recording_stop)
                )
            }
        } else {
            // Start Button: Medium emphasis, the default state.
            FilledTonalIconButton(
                onClick = onStartRecording,
                enabled = enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(id = R.string.recording_start)
                )
            }
        }
    }
}