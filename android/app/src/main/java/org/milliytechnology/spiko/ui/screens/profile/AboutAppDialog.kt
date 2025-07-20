package org.milliytechnology.spiko.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.utils.getAppVersion

@Composable
fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember { getAppVersion(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Use the round launcher icon, which often fits better in dialogs.
        // A size of 40.dp provides good visual balance.
        icon = {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_round),
                contentDescription = stringResource(id = R.string.logo_app),
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            // Ensure the title is centered, especially if it wraps to multiple lines.
            Text(
                text = stringResource(id = R.string.about_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            // Center the text content to align with the centered icon and title.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.about_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Use a more subtle color for metadata like version and copyright.
                Text(
                    text = stringResource(id = R.string.about_version, appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    // NOTE: Ensure this string resource exists in your strings.xml
                    // e.g., <string name="about_copyright">© 2025 MilliyTechnology LLC</string>
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            // The confirm button is already aligned correctly by the AlertDialog.
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.button_ok))
            }
        }
    )
}