package com.typosbro.multilevel.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout // Import Logout icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.data.remote.models.ChatSummary
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel // Import AuthViewModel
import com.typosbro.multilevel.ui.viewmodels.ChatListViewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNavigateToChat: (chatId: String) -> Unit,
    onLogout: () -> Unit, // Callback for logout
    chatListViewModel: ChatListViewModel = viewModel(factory = AppViewModelProvider.Factory),
    authViewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory) // Get AuthViewModel instance
) {
    val chats by chatListViewModel.chats.collectAsStateWithLifecycle()
    val isLoading by chatListViewModel.isLoading.collectAsStateWithLifecycle()
    val error by chatListViewModel.error.collectAsStateWithLifecycle()
    val navigateToChatId by chatListViewModel.navigateToChatId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() } // For showing errors or confirmations
    val coroutineScope = rememberCoroutineScope()

    // --- State for Dialogs ---
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<ChatSummary?>(null) } // Hold chat to delete


    LaunchedEffect(navigateToChatId) {
        navigateToChatId?.let { chatId ->
            onNavigateToChat(chatId)
            chatListViewModel.consumedNavigation() // Reset navigation trigger
        }
    }

    LaunchedEffect(error) {
        error?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Short
                )
            }
            chatListViewModel.clearError() // Clear error after showing
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Chats") },
                actions = {
                    // Logout Button
                    IconButton(onClick = {
                        authViewModel.logout() // Call logout on AuthViewModel
                        onLogout() // Trigger navigation/state update in AppNavigation
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create New Chat")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) } // Add SnackbarHost
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && chats.isEmpty()) { // Show loading only when initially loading empty list
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (chats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No chats yet. Tap '+' to create one.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chats, key = { it._id }) { chat ->
                        ChatListItem(
                            chat = chat,
                            onClick = { onNavigateToChat(chat._id) },
                            onDeleteClick = { showDeleteConfirmDialog = chat } // Show confirmation dialog
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (showCreateDialog) {
        CreateChatDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                chatListViewModel.createNewChat(title.ifBlank { null }) // Pass null if blank
                showCreateDialog = false
            }
        )
    }

    showDeleteConfirmDialog?.let { chatToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete Chat?") },
            text = { Text("Are you sure you want to delete '${chatToDelete.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        chatListViewModel.deleteChat(chatToDelete._id)
                        showDeleteConfirmDialog = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Chat deleted", duration = SnackbarDuration.Short)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun ChatListItem(
    chat: ChatSummary,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(chat.title, maxLines = 1) },
        // supportingContent = { Text("Updated: ${formatTimestamp(chat.updatedAt)}") }, // Add timestamp formatting
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Chat", tint = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
fun CreateChatDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Chat Title (Optional)") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(title) }) {
                Text("Create")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}