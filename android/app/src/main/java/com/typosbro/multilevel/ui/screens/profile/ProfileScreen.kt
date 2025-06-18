// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/profile/ProfileScreen.kt
package com.typosbro.multilevel.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.ui.viewmodels.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel, // Injected from MainScreen
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Formatter for the join date
    val formattedDate = remember(uiState.userProfile.createdAt) {
        if (uiState.userProfile.createdAt.isEmpty()) {
            "..."
        } else {
            try {
                // Input format from server (ISO 8601)
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(uiState.userProfile.createdAt)
                // Desired output format
                val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                date?.let { formatter.format(it) } ?: "..."
            } catch (e: Exception) {
                "Invalid date" // Fallback
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile & Settings") }) }
    ) { padding ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // --- Account Section ---
            item {
                SectionHeader("Account")
                ListItem(
                    headlineContent = { Text("Email") },
                    supportingContent = { Text(uiState.userProfile.email) },
                    leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = "Email") }
                )
                ListItem(
                    headlineContent = { Text("Member Since") },
                    supportingContent = { Text(formattedDate) },
                    leadingContent = { Icon(Icons.Default.Event, contentDescription = "Member Since") }
                )
                ListItem(
                    headlineContent = { Text("Subscription", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Free Tier") },
                    leadingContent = { Icon(Icons.Default.WorkspacePremium, contentDescription = "Subscription") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { /* Navigate to subscription screen */ }
                )
            }

            // --- Other sections remain the same ---
            item {
                SectionHeader("Settings")
                ListItem(
                    headlineContent = { Text("Dark Mode") },
                    leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = "Dark Mode") },
                    trailingContent = {
                        Switch(checked = uiState.isDarkTheme, onCheckedChange = { viewModel.onThemeChanged(it) })
                    }
                )
            }
            item {
                SectionHeader("Support & Legal")
                ListItem(
                    headlineContent = { Text("Help & FAQ") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help") },
                    modifier = Modifier.clickable { /* Navigate to Help screen */ }
                )
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    leadingContent = { Icon(Icons.Default.PrivacyTip, contentDescription = "Privacy Policy") },
                    modifier = Modifier.clickable { /* Open URL */ }
                )
                ListItem(
                    headlineContent = { Text("About This App") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = "About") },
                    modifier = Modifier.clickable { /* Show About dialog */ }
                )

            }
            item {
                Spacer(Modifier.height(32.dp))
                ListItem(
                    headlineContent = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showLogoutDialog = true }
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                onLogout()
                showLogoutDialog = false
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

// ... SectionHeader and LogoutConfirmationDialog composables are unchanged ...
@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Logout") },
        text = { Text("Are you sure you want to log out?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Logout") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}