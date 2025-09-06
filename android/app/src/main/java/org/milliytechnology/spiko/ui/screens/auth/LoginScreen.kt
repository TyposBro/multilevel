// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/screens/auth/LoginScreen.kt
package org.milliytechnology.spiko.ui.screens.auth

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.component.DividerWithText
import org.milliytechnology.spiko.ui.component.GoogleSignInButton
import org.milliytechnology.spiko.ui.component.TelegramSignInButton
import org.milliytechnology.spiko.ui.viewmodels.AuthViewModel
import org.milliytechnology.spiko.ui.viewmodels.UiState

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel
) {
    val googleSignInState by authViewModel.googleSignInState.collectAsState()
    val isLoading = googleSignInState is UiState.Loading
    val context = LocalContext.current
    val googleWebClientId = stringResource(R.string.google_web_client_id)
    val googleErrorText = stringResource(id = R.string.login_google_error)

    // --- Reviewer Login State ---
    var tapCount by remember { mutableIntStateOf(0) }
    var showReviewerDialog by remember { mutableStateOf(false) }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(googleWebClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { authViewModel.signInWithGoogle(it) }
        } catch (e: ApiException) {
            Log.w("LoginScreen", "Google sign in failed", e)
            authViewModel.setGoogleSignInError(googleErrorText)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.login_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                // --- Easter Egg Trigger ---
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // No ripple effect
                ) {
                    tapCount++
                    if (tapCount > 6) { // Show on the 7th tap
                        showReviewerDialog = true
                        tapCount = 0 // Reset counter
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))

            // Use the encapsulated and themed Google Sign-In button
            GoogleSignInButton(
                isLoading = isLoading,
                onClick = {
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            DividerWithText(
                text = stringResource(id = R.string.login_or),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Use the encapsulated and themed Telegram Sign-In button
            TelegramSignInButton(
                enabled = !isLoading,
                onClick = {
                    val botUsername = "milliy_technology_bot"
                    val intent = Intent(Intent.ACTION_VIEW, "https://t.me/$botUsername".toUri())
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("LoginScreen", "Failed to open Telegram", e)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Terms and Privacy links
            val termsUrl = stringResource(id = R.string.url_terms)
            val privacyUrl = stringResource(id = R.string.url_privacy_policy)
            val termsLabel = stringResource(id = R.string.login_terms_label_terms)
            val privacyLabel = stringResource(id = R.string.login_terms_label_privacy)
            val termsTextTemplate = stringResource(id = R.string.login_terms_text)

            val annotatedText = androidx.compose.ui.text.buildAnnotatedString {
                val formatted = String.format(termsTextTemplate, termsLabel, privacyLabel)
                append(formatted)
                // find indices for labels and add url annotations
                val termsStart = formatted.indexOf(termsLabel)
                if (termsStart >= 0) {
                    val termsEnd = termsStart + termsLabel.length
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary),
                        start = termsStart,
                        end = termsEnd
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = termsUrl,
                        start = termsStart,
                        end = termsEnd
                    )
                }
                val privacyStart = formatted.indexOf(privacyLabel)
                if (privacyStart >= 0) {
                    val privacyEnd = privacyStart + privacyLabel.length
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary),
                        start = privacyStart,
                        end = privacyEnd
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = privacyUrl,
                        start = privacyStart,
                        end = privacyEnd
                    )
                }
            }

            androidx.compose.foundation.text.ClickableText(
                text = annotatedText,
                onClick = { offset ->
                    val annotations = annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    val url = annotations.firstOrNull()?.item
                    if (url != null) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("LoginScreen", "Failed to open link: $url", e)
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            val errorMessage = (googleSignInState as? UiState.Error)?.message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showReviewerDialog) {
        ReviewerLoginDialog(
            viewModel = authViewModel,
            onDismiss = {
                showReviewerDialog = false
                authViewModel.resetAllStates() // Clear any errors from the dialog
            }
        )
    }
}

@Composable
private fun ReviewerLoginDialog(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val reviewerState by viewModel.reviewerLoginState.collectAsState()

    // When login is successful, dismiss the dialog
    LaunchedEffect(reviewerState) {
        if (reviewerState is UiState.Success) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reviewer Login") },
        text = {
            Column {
                Text("Please enter the reviewer username provided in the Play Console instructions.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Reviewer Username") },
                    singleLine = true,
                    isError = reviewerState is UiState.Error
                )
                if (reviewerState is UiState.Error) {
                    Text(
                        text = (reviewerState as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.loginAsReviewer(email) },
                enabled = reviewerState !is UiState.Loading
            ) {
                if (reviewerState is UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Login")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = reviewerState !is UiState.Loading
            ) {
                Text("Cancel")
            }
        }
    )
}