// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/chat/ChatDetailScreen.kt
package com.typosbro.multilevel.ui.screens.chat


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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.ui.component.ChatMessageBubble
import com.typosbro.multilevel.ui.component.RecognitionControls
import com.typosbro.multilevel.ui.viewmodels.ChatDetailViewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel()
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
            }
        })

    // --- Effects ---
    LaunchedEffect(uiState.messageList.size) {
        if (uiState.messageList.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            }
            viewModel.clearError()
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
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.messageList.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 8.dp
                    )
                ) {
                    if (uiState.isRecording && uiState.partialText.isNotBlank()) {
                        item {
                            ChatMessageBubble(
                                message = com.typosbro.multilevel.ui.component.ChatMessage(
                                    text = uiState.partialText + "...",
                                    isUser = true
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    items(
                        items = uiState.messageList, key = { message -> message.id }) { message ->
                        ChatMessageBubble(message = message)
                    }
                }
            }

            // --- Recording Control Area ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- FIX: The timer display is removed as it's no longer in the UI state ---
                // uiState.remainingRecordingTime?.let { ... } // This block is deleted

                // Recognition Controls
                RecognitionControls(
                    isRecording = uiState.isRecording,
                    onStartRecording = {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (!uiState.isModelReady) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Voice model not ready yet.") }
                        } else {
                            viewModel.startMicRecognition()
                        }
                    },
                    onStopRecording = {
                        viewModel.stopRecognitionAndSend()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}