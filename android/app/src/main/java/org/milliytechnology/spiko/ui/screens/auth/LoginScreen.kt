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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.typosbro.multilevel.R
import com.typosbro.multilevel.ui.component.DividerWithText
import com.typosbro.multilevel.ui.component.GoogleSignInButton
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel
import com.typosbro.multilevel.ui.viewmodels.UiState

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // --- State Management ---
    val googleSignInState by authViewModel.googleSignInState.collectAsState()
    // You might have a state for when the app is waiting for the deep link token verification
    // val deepLinkVerifyState by authViewModel.deepLinkVerifyState.collectAsState()
    val isLoading =
        googleSignInState is UiState.Loading // || deepLinkVerifyState is UiState.Loading

    // --- Google Sign-In Setup ---
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

    // --- UI ---
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
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = stringResource(id = R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))

            // --- Google Sign-In Button ---
            GoogleSignInButton(
                isLoading = googleSignInState is UiState.Loading,
                onClick = {
                    if (!isLoading) googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Divider ---
            DividerWithText(
                text = stringResource(id = R.string.login_or),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Telegram Native Login Button ---
            TelegramNativeLoginButton(
                enabled = !isLoading,
                onClick = {
                    val botUsername = "milliy_technology_bot" // Your bot username
                    // This intent opens the Telegram app directly to your bot.
                    // Telegram will send a webhook to your backend when the user clicks "Start".
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://t.me/$botUsername".toUri()
                    )
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Handle case where Telegram is not installed
                        Log.e("LoginScreen", "Failed to open Telegram", e)
                        // You could show a Toast or Snackbar message here.
                    }
                }
            )

            // --- Error Display ---
            // You would also check the deepLinkVerifyState error here in a real implementation
            val googleError = (googleSignInState as? UiState.Error)?.message
            val errorMessage = googleError

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


/**
 * A custom styled button for initiating the native Telegram login flow.
 */
@Composable
private fun TelegramNativeLoginButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF54A9E9), // Official Telegram Blue
            contentColor = Color.White
        )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_telegram_logo), // You need to add this drawable
            contentDescription = null, // decorative
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified // Icon color
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.login_telegram),
            fontWeight = FontWeight.Bold
        )
    }
}