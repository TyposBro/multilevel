package com.typosbro.multilevel.ui.screens.auth

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel
import com.typosbro.multilevel.ui.viewmodels.UiState

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // --- Local UI State ---
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    // State for client-side validation errors (e.g., password mismatch)
    var localError by remember { mutableStateOf<String?>(null) }

    // --- ViewModel State ---
    val registrationState by authViewModel.registrationState.collectAsState()
    val isLoading = registrationState is UiState.Loading
    val isFormEnabled = !isLoading

    // --- UI ---
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp), // Consistent padding
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Create Account", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Get started by filling out the form below",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // --- Form Fields ---
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null }, // Clear error on change
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isFormEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = isFormEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; localError = null },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = isFormEnabled,
                // Highlight field if there's a local validation error
                isError = localError != null
            )
            Spacer(modifier = Modifier.height(24.dp))

            // --- Register Button ---
            Button(
                onClick = {
                    // --- Client-side validation before calling ViewModel ---
                    if (password != confirmPassword) {
                        localError = "Passwords do not match."
                        return@Button
                    }
                    if (password.length < 6) {
                        localError = "Password must be at least 6 characters."
                        return@Button
                    }
                    // If validation passes, clear local error and call ViewModel
                    localError = null
                    authViewModel.register(email, password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank() && isFormEnabled
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create Account")
                }
            }

            // --- Error Display ---
            // Prioritize local errors, then show backend errors
            val backendError = (registrationState as? UiState.Error)?.message
            val errorMessage = localError ?: backendError

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes the login button down

            // --- Login Button ---
            TextButton(onClick = onNavigateToLogin, enabled = isFormEnabled) {
                Text("Already have an account? Login")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}