// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/screens/auth/LoginScreen.kt
package com.typosbro.multilevel.ui.screens.auth

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    onNavigateToRegister: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // --- State Management ---
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // It's better to unify the UI state in the ViewModel, but for now we'll combine them here.
    val emailLoginState by authViewModel.loginState.collectAsState()
    val googleSignInState by authViewModel.googleSignInState.collectAsState()

    // Determine overall loading state
    val isLoading = emailLoginState is UiState.Loading || googleSignInState is UiState.Loading
    val isFormEnabled = !isLoading

    // --- Google Sign-In Setup ---
    val context = LocalContext.current
    val googleWebClientId = stringResource(R.string.google_web_client_id)

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
            authViewModel.setGoogleSignInError("Google sign in failed. Please try again.")
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
                .padding(horizontal = 24.dp), // More horizontal padding
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text("Welcome Back", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Sign in to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // --- Email & Password Form ---
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isFormEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = isFormEnabled
            )
            Spacer(modifier = Modifier.height(24.dp))

            // --- Login Button ---
            Button(
                onClick = { authViewModel.login(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = email.isNotBlank() && password.isNotBlank() && isFormEnabled
            ) {
                if (emailLoginState is UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Login")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Divider ---
            DividerWithText(
                text = "OR",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Google Sign-In Button ---
            GoogleSignInButton(
                isLoading = googleSignInState is UiState.Loading,
                onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) }
            )

            // --- Error Display ---
            val emailError = (emailLoginState as? UiState.Error)?.message
            val googleError = (googleSignInState as? UiState.Error)?.message
            val errorMessage = emailError ?: googleError

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes the register button down

            // --- Register Button ---
            TextButton(onClick = onNavigateToRegister, enabled = isFormEnabled) {
                Text("Don't have an account? Register")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// In your AuthViewModel, you might need a way to set an error from the LoginScreen
// fun setGoogleSignInError(message: String) {
//     _googleSignInState.value = UiState.Error(message)
// }