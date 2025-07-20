// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/component/DividerWithText.kt
package org.milliytechnology.spiko.ui.component


import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A composable that displays a horizontal line with text in the middle.
 * It uses Material 3's default Divider color and themed text styling.
 *
 * @param text The text to display in the middle of the divider.
 * @param modifier The modifier to be applied to the row layout.
 */
@Composable
fun DividerWithText(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The M3 Divider automatically uses the `outlineVariant` color from the theme.
        Divider(modifier = Modifier.weight(1f))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp) // Increased padding for better spacing
        )
        Divider(modifier = Modifier.weight(1f))
    }
}