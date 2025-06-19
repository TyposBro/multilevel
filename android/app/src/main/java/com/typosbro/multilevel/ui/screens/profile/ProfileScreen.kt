package com.typosbro.multilevel.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.R
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel
import com.typosbro.multilevel.ui.viewmodels.ProfileViewModel
import com.typosbro.multilevel.ui.viewmodels.UiState
import com.typosbro.multilevel.utils.openUrlInCustomTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val userProfile = uiState.userProfile


    // --- State for Dialogs ---
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) } // NEW state for About dialog

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current // Get context for the URL launcher

    val privacyPolicyUrl =
        stringResource(R.string.url_privacy_policy) // Fetch the URL from resources

    // This effect handles the success of the account deletion by logging out.
    LaunchedEffect(uiState.deleteState) {
        if (uiState.deleteState is UiState.Success) {
            coroutineScope.launch {
                authViewModel.logout()
                // Navigation will happen automatically.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                actions = {
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

            // --- Support Section ---
            item {
                SectionHeader("Support & About")
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
                // --- PRIVACY POLICY ---
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    leadingContent = {
                        Icon(
                            Icons.Default.PrivacyTip,
                            contentDescription = "Privacy Policy"
                        )
                    },
                    modifier = Modifier.clickable {
                        openUrlInCustomTab(context, privacyPolicyUrl)
                    }
                )
                // --- ABOUT APP ---
                ListItem(
                    headlineContent = { Text("About This App") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = "About") },
                    modifier = Modifier.clickable { showAboutDialog = true } // Show the dialog
                )
            }

            // --- Actions Section ---
            item {
                SectionHeader("Actions")
                // --- LOGOUT BUTTON ---
                ListItem(
                    headlineContent = { Text("Logout") },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout"
                        )
                    },
                    modifier = Modifier.clickable { showLogoutDialog = true }
                )
                // --- DELETE ACCOUNT BUTTON ---
                ListItem(
                    headlineContent = {
                        Text(
                            "Delete Account",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = { Text("This action is permanent") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Delete Account",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { showDeleteDialog = true }
                )
                Spacer(Modifier.height(32.dp))
            }
        }

        val deleteError = (uiState.deleteState as? UiState.Error)?.message
        val fetchError = uiState.error
        val errorMessage = deleteError ?: fetchError

        errorMessage?.let {
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

    // --- DIALOGS ---
    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                coroutineScope.launch {
                    authViewModel.logout()
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    if (showDeleteDialog) {
        DeleteAccountConfirmationDialog(
            isLoading = uiState.deleteState is UiState.Loading,
            onConfirm = {
                profileViewModel.deleteAccount()
            },
            onDismiss = {
                if (uiState.deleteState !is UiState.Loading) {
                    showDeleteDialog = false
                    profileViewModel.resetDeleteState()
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }
}


// --- HELPER COMPOSABLES ---

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
            Button(onClick = onConfirm) { Text("Logout") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteAccountConfirmationDialog(
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Your Account?") },
        text = { Text("This action is permanent. All of your data will be deleted forever. This cannot be undone.\n\nAre you sure you want to proceed?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Text("Delete Permanently")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}