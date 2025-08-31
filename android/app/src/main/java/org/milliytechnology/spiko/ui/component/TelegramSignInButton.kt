// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/component/TelegramSignInButton.kt
package org.milliytechnology.spiko.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.milliytechnology.spiko.R

/**
 * A custom styled button for initiating the native Telegram login flow.
 * Uses the primary theme color for high emphasis.
 */
@Composable
fun TelegramSignInButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp), // A consistent height for auth buttons
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_telegram_logo),
            contentDescription = null, // decorative
            modifier = Modifier.size(24.dp),
            // The default tint (LocalContentColor) works perfectly here
            // as it will be MaterialTheme.colorScheme.onPrimary
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.login_telegram),
            style = MaterialTheme.typography.labelLarge
        )
    }
}