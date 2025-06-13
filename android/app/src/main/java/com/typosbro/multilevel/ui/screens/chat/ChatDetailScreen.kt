package com.typosbro.multilevel.ui.screens.chat

// import com.typosbro.multilevel.ui.component.TimerModal
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.typosbro.multilevel.ui.component.ChatMessageBubble
import com.typosbro.multilevel.ui.component.RecognitionControls
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider
import com.typosbro.multilevel.ui.viewmodels.ChatDetailViewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    onNavigateBack: () -> Unit,
    // chatId is handled by ViewModel's SavedStateHandle now
    viewModel: ChatDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // --- Permission Handling ---
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(), onResult = { isGranted ->
            hasAudioPermission = isGranted
            if (!isGranted) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Audio permission required for voice input.",
                        duration = SnackbarDuration.Long
                    )
                }
            } else {
                // Permission granted, maybe trigger something if needed, but usually just allows action
            }
        })

    // --- Effects ---
    LaunchedEffect(uiState.messageList.size) { // Scroll when new messages arrive
        if (uiState.messageList.isNotEmpty()) {
            // Scroll to the newest message (which is at index 0 because of reverseLayout)
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(uiState.error) { // Show errors in Snackbar
        uiState.error?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            }
            viewModel.clearError() // Clear error after showing
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(uiState.chatTitle, maxLines = 1) }, navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        })
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
        ) {
            // Loading indicator for initial history load
            if (uiState.isLoading && uiState.messageList.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Chat Messages Area
                LazyColumn(
                    modifier = Modifier
                        .weight(1f) // Takes up remaining space
                        .fillMaxWidth(), // Ensures it fills width
                    state = listState,
                    reverseLayout = true, // Newest messages at the bottom (displayed at top of list visually)
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 8.dp
                    ) // Padding for messages
                ) {
                    // Partial text bubble (if recording and partial text exists)
                    if (uiState.isRecording && uiState.partialText.isNotBlank()) {
                        item {
                            ChatMessageBubble(
                                message = com.typosbro.multilevel.ui.component.ChatMessage( // Use explicit import
                                    text = uiState.partialText + "...", // Indicate it's partial
                                    isUser = true
                                ),
                                modifier = Modifier.padding(bottom = 4.dp) // Spacing below partial
                            )
                        }
                    }

                    // Render actual messages
                    items(
                        items = uiState.messageList, key = { message -> message.id }) { message ->
                        ChatMessageBubble(message = message)
                    }
                }
            }

            // Spacer removed, padding handled by Column/RecognitionControls surface

            // --- Recording Control Area ---
            Column(
                modifier = Modifier.fillMaxWidth()
                // Add vertical padding if needed, RecognitionControls usually has internal padding
                // .padding(vertical = 8.dp)
                , horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ** Display Remaining Time **
                // Conditionally display the Text based on remainingRecordingTime
                uiState.remainingRecordingTime?.let { time ->
                    Text(
                        text = "$time s",
                        style = MaterialTheme.typography.labelMedium, // Or bodySmall, etc.
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // Use theme color
                        modifier = Modifier.padding(bottom = 4.dp) // Space between timer and button
                    )
                }

                // Recognition Controls
                RecognitionControls(
                    isRecording = uiState.isRecording,
                    onStartRecording = {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (!uiState.isModelReady) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Voice model not ready yet.") }
                        } else {
                            // Start recording
                            viewModel.startMicRecognition()
                        }
                    },
                    onStopRecording = {
                        // Stop recording
                        viewModel.stopRecognitionAndSend()
                    },
                    modifier = Modifier.fillMaxWidth() // Let RecognitionControls handle its own padding/surface
                )
            }
        }
    }
}

