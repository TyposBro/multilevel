package com.typosbro.multilevel.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.R
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel
import com.typosbro.multilevel.ui.viewmodels.ProfileViewModel
import com.typosbro.multilevel.ui.viewmodels.SettingsViewModel
import com.typosbro.multilevel.ui.viewmodels.UiState
import com.typosbro.multilevel.utils.openUrlInCustomTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val userProfile = uiState.userProfile

    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsStateWithLifecycle()
    val currentLanguageCode by settingsViewModel.currentLanguageCode.collectAsStateWithLifecycle()

    // --- State for Dialogs ---
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val privacyPolicyUrl = stringResource(R.string.url_privacy_policy)
    val faqUrl = stringResource(R.string.url_faq)

    LaunchedEffect(uiState.deleteState) {
        if (uiState.deleteState is UiState.Success) {
            coroutineScope.launch {
                authViewModel.logout()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.profile_title)) },
                actions = {
                    IconButton(
                        onClick = { profileViewModel.fetchUserProfile() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(id = R.string.button_refresh)
                        )
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
                SectionHeader(title = stringResource(id = R.string.profile_section_title_account))
                // --- CORRECTED: Use the new display fields from UserProfileViewData ---
                ListItem(
                    headlineContent = { Text(text = userProfile?.displayName ?: "...") },
                    supportingContent = { Text(userProfile?.primaryIdentifier ?: "...") },
                    leadingContent = {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "User Profile"
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text(text = "Authentication Method") },
                    supportingContent = { Text(userProfile?.authProvider ?: "...") },
                    leadingContent = {
                        when (userProfile?.authProvider?.lowercase()) {
                            "google" -> Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Login",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified // Use default color
                            )

                            "telegram" -> Icon(
                                painter = painterResource(id = R.drawable.ic_telegram_logo),
                                contentDescription = "Telegram Login",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified // Use default color
                            )

                            else -> Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Default Login Method"
                            )
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.profile_item_membership)) },
                    supportingContent = { Text(userProfile?.registeredDate ?: "...") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = stringResource(id = R.string.profile_item_membership)
                        )
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.profile_item_subscription),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    supportingContent = { Text(text = stringResource(id = R.string.tier_free)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = stringResource(id = R.string.profile_item_subscription)
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { /* TODO: Navigate to subscription screen */ }
                )
            }

            // --- Settings Section ---
            item {
                SectionHeader(title = stringResource(id = R.string.profile_section_title_settings))
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.profile_item_dark_mode)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription = stringResource(id = R.string.profile_item_dark_mode)
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { isChecked ->
                                settingsViewModel.onThemeChanged(isChecked)
                            }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.profile_item_language)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = stringResource(id = R.string.profile_item_language)
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = (currentLanguageCode ?: "en").uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.clickable { showLanguageDialog = true }
                )
            }

            // --- Support Section ---
            item {
                SectionHeader(title = stringResource(id = R.string.profile_section_title_support))
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.profile_item_help)) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(id = R.string.profile_item_help)
                        )
                    },
                    modifier = Modifier.clickable { openUrlInCustomTab(context, faqUrl) }
                )
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.profile_item_privacy)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.PrivacyTip,
                            contentDescription = stringResource(id = R.string.profile_item_privacy)
                        )
                    },
                    modifier = Modifier.clickable { openUrlInCustomTab(context, privacyPolicyUrl) }
                )

                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.profile_item_about)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(id = R.string.profile_item_about)
                        )
                    },
                    modifier = Modifier.clickable { showAboutDialog = true }
                )
            }

            // --- Actions Section ---
            item {
                SectionHeader(title = stringResource(id = R.string.profile_section_title_action))
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.button_logout)) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(id = R.string.button_logout)
                        )
                    },
                    modifier = Modifier.clickable { showLogoutDialog = true }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.profile_item_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = { Text(text = stringResource(id = R.string.profile_item_subtitle_permanent)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(id = R.string.profile_item_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { showDeleteDialog = true }
                )
                Spacer(Modifier.height(32.dp))
            }
        }

        // --- Error Message Display ---
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

    // Add this to your composable to prevent showing multiple dialogs
    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onLanguageSelected = { languageCode ->
                settingsViewModel.onLanguageChanged(languageCode)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}


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
        title = { Text(text = stringResource(id = R.string.logout_confirm_title)) },
        text = { Text(text = stringResource(id = R.string.logout_confirm_subtitle)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(text = stringResource(id = R.string.button_logout)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(id = R.string.button_cancel)) }
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
                contentDescription = stringResource(id = R.string.button_warning),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(text = stringResource(id = R.string.delete_confirm_title)) },
        text = { Text(text = stringResource(id = R.string.delete_confirm_subtitle)) },
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
                    Text(text = stringResource(id = R.string.delete_permanent))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) { Text(text = stringResource(id = R.string.button_cancel)) }
        }
    )
}

@Composable
private fun LanguageSelectionDialog(
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val supportedLanguages = mapOf(
        "en" to "English",
        "ru" to "Русский",
        "uz" to "O'zbekcha"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.language_dialog_title)) },
        text = {
            Column {
                supportedLanguages.forEach { (code, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        modifier = Modifier.clickable { onLanguageSelected(code) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.button_cancel))
            }
        }
    )
}
