// {PATH_TO_PROJECT}/app/src/main/java/org/milliytechnology/spiko/ui/screens/auth/LoginScreen.kt
package org.milliytechnology.spiko.ui.screens.auth

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
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
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val googleSignInState by authViewModel.googleSignInState.collectAsState()
    val isLoading = googleSignInState is UiState.Loading
    val context = LocalContext.current
    val googleWebClientId = stringResource(R.string.google_web_client_id)
    val googleErrorText = stringResource(id = R.string.login_google_error)

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
                color = MaterialTheme.colorScheme.primary
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
                        // Optionally show a toast or snackbar to the user
                    }
                }
            )

            // --- Error Display ---
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
}