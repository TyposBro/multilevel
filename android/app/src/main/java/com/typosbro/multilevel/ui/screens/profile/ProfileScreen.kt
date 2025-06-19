package com.typosbro.multilevel.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel
import com.typosbro.multilevel.ui.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    // --- CHANGE: Use Hilt to inject ViewModels directly ---
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // --- CHANGE: Read from the new ProfileUiState ---
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val userProfile = uiState.userProfile // This is now a UserProfileViewData?

    var showLogoutDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                actions = {
                    // --- NEW: Add a refresh button, tied to the ViewModel function ---
                    IconButton(
                        onClick = { profileViewModel.fetchUserProfile() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Profile")
                    }
                }
            )
        }
    ) { padding ->

        if (uiState.isLoading && userProfile == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding), contentAlignment = Alignment.Center
            ) {
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

                // --- CHANGE: Use data from the new userProfile object ---
                ListItem(
                    headlineContent = { Text("Email") },
                    supportingContent = { Text(userProfile?.email ?: "...") },
                    leadingContent = {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Email"
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Member Since") },
                    supportingContent = { Text(userProfile?.registeredDate ?: "...") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = "Member Since"
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Subscription", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Free Tier") },
                    leadingContent = {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = "Subscription"
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { /* Navigate to subscription screen */ }
                )
            }

            item {
                SectionHeader("Settings")
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text("Light/Dark (Coming Soon)") }, // Placeholder
                    leadingContent = {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription = "Dark Mode"
                        )
                    }
                )
            }

            // --- Support Section (Unchanged) ---
            item {
                SectionHeader("Support & Legal")
                ListItem(
                    headlineContent = { Text("Help & FAQ") },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help"
                        )
                    },
                    modifier = Modifier.clickable { /* Navigate to Help screen */ }
                )
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    leadingContent = {
                        Icon(
                            Icons.Default.PrivacyTip,
                            contentDescription = "Privacy Policy"
                        )
                    },
                    modifier = Modifier.clickable { /* Open URL */ }
                )
            }

            // --- Logout Section ---
            item {
                Spacer(Modifier.height(32.dp))
                ListItem(
                    headlineContent = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { showLogoutDialog = true }
                )
                Spacer(Modifier.height(32.dp))
            }
        }

        // --- Error Display ---
        // A simple way to show errors from the backend at the bottom of the screen
        uiState.error?.let {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                // --- CHANGE: Call the suspend function from the AuthViewModel ---
                coroutineScope.launch {
                    authViewModel.logout()
                    // No need to call an onLogout callback. The AppNavigation will react automatically.
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

// The helper composables remain exactly the same.
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