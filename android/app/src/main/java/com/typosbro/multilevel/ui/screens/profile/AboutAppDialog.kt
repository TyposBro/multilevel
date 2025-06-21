package com.typosbro.multilevel.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.typosbro.multilevel.R
import com.typosbro.multilevel.utils.getAppVersion

@Composable
fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember { getAppVersion(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Use your app's icon
        icon = {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_round),
                contentDescription = "App Icon",
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text("About Multilevel") },
        text = {
            Column {
                Text(
                    text = "A multi-level English learning and practice application.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Version: $appVersion",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "© 2025 TyposBro",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}