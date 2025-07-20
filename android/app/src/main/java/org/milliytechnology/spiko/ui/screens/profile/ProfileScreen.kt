package org.milliytechnology.spiko.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.viewmodels.AuthViewModel
import org.milliytechnology.spiko.ui.viewmodels.ProfileViewModel
import org.milliytechnology.spiko.ui.viewmodels.SettingsViewModel
import org.milliytechnology.spiko.ui.viewmodels.UiState
import org.milliytechnology.spiko.utils.openUrlInCustomTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSubscription: () -> Unit,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val userProfile = uiState.userProfile

    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsStateWithLifecycle()
    val currentLanguageCode by settingsViewModel.currentLanguageCode.collectAsStateWithLifecycle()

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
            item { SettingsCategoryHeader(title = stringResource(id = R.string.profile_section_title_account)) }

            item {
                ListItem(
                    headlineContent = { Text(text = userProfile?.displayName ?: "...") },
                    supportingContent = { Text(userProfile?.primaryIdentifier ?: "...") },
                    leadingContent = {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "User Profile",
                            modifier = Modifier.size(40.dp) // Slightly larger icon for profile header
                        )
                    },
                    colors = ListItemDefaults.colors(
                        leadingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(text = "Authentication Method") },
                    supportingContent = { Text(userProfile?.authProvider ?: "...") },
                    leadingContent = {
                        when (userProfile?.authProvider?.lowercase()) {
                            "google" -> Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Login",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )

                            "telegram" -> Icon(
                                painter = painterResource(id = R.drawable.ic_telegram_logo),
                                contentDescription = "Telegram Login",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )

                            else -> Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Default Login Method"
                            )
                        }
                    }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Event,
                    title = stringResource(id = R.string.profile_item_membership),
                    subtitle = userProfile?.registeredDate ?: "..."
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = stringResource(id = R.string.profile_item_subscription),
                    subtitle = stringResource(id = R.string.tier_free),
                    onClick = onNavigateToSubscription
                )
            }


            // --- Settings Section ---
            item { SettingsCategoryHeader(title = stringResource(id = R.string.profile_section_title_settings)) }

            item {
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(id = R.string.profile_item_dark_mode)
                ) {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { settingsViewModel.onThemeChanged(it) }
                    )
                }
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(id = R.string.profile_item_language),
                    onClick = { showLanguageDialog = true }
                ) {
                    Text(
                        text = currentLanguageCode.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // --- Support Section ---
            item { SettingsCategoryHeader(title = stringResource(id = R.string.profile_section_title_support)) }

            item {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = stringResource(id = R.string.profile_item_help),
                    onClick = { openUrlInCustomTab(context, faqUrl) }
                )
                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = stringResource(id = R.string.profile_item_privacy),
                    onClick = { openUrlInCustomTab(context, privacyPolicyUrl) }
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(id = R.string.profile_item_about),
                    onClick = { showAboutDialog = true }
                )
            }

            // --- Actions Section ---
            item { SettingsCategoryHeader(title = stringResource(id = R.string.profile_section_title_action)) }

            item {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = stringResource(id = R.string.button_logout),
                    onClick = { showLogoutDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Warning,
                    title = stringResource(id = R.string.profile_item_delete),
                    subtitle = stringResource(id = R.string.profile_item_subtitle_permanent),
                    titleColor = MaterialTheme.colorScheme.error,
                    iconColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteDialog = true }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
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

/**
 * A reusable Material 3-style header for categorizing settings items.
 */
@Composable
private fun SettingsCategoryHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * A reusable, opinionated settings item that enforces Material 3 design consistency.
 * It simplifies creating clickable list items with icons, text, and optional trailing content.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(text = title, color = titleColor) },
        supportingContent = if (subtitle != null) {
            { Text(text = subtitle) }
        } else null,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor
            )
        },
        trailingContent = {
            if (onClick != null && trailingContent == null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                trailingContent?.invoke()
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
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

/**
 * A simplified language selection dialog. Clicking an item selects it and dismisses the dialog.
 * Dismissing the dialog by clicking outside or pressing back acts as "Cancel".
 */
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        // A "Cancel" button is removed to follow a more modern UX pattern where
        // dismissal (clicking outside) serves the same purpose.
        confirmButton = {}
    )
}