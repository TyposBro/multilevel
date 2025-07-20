// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/component/GoogleSignInButton.kt
package org.milliytechnology.spiko.ui.component


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.milliytechnology.spiko.R

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp), // A consistent height for auth buttons
        shape = MaterialTheme.shapes.medium,
        enabled = !isLoading
    ) {
        AnimatedContent(
            targetState = isLoading,
            label = "GoogleButtonContent",
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                        fadeOut(animationSpec = tween(90))
            }
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = null, // The button text describes the action
                        modifier = Modifier.size(24.dp),
                        // Use Color.Unspecified to render the original colors of the vector asset
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(id = R.string.login_google),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}