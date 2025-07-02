package com.typosbro.multilevel.ui.screens.subscription

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typosbro.multilevel.R
import com.typosbro.multilevel.ui.viewmodels.SubscriptionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalContext.current as ComponentActivity

    val lifecycleOwner = LocalLifecycleOwner.current

    // This effect will run when the screen enters the composition
    // and clean up when it leaves.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Check if the event is ON_RESUME
            if (event == Lifecycle.Event.ON_RESUME) {
                // When the app resumes, it might be because the user
                // is coming back from the Payme browser flow.
                // We should check if there's a pending transaction to verify.

                // TODO: Here you would first check your local storage
                // if (paymentPrefManager.hasPendingTransaction()) {
                //    viewModel.verifyPendingPurchase("payme")
                // }

                // For demonstration, we can log this event.
                Log.d("SubscriptionScreen", "App Resumed. Time to check for pending payments.")
            }
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // The onDispose block is called when the composable leaves the screen
        onDispose {
            // Remove the observer to prevent memory leaks
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    // Observe state for showing messages (Toast/Snackbar)
    LaunchedEffect(uiState.purchaseSuccessMessage) {
        uiState.purchaseSuccessMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Upgrade Subscription") }, navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.button_back)
                    )
                }
            })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    SubscriptionTierCard(
                        tierName = "Silver",
                        price = "15,000 UZS / month",
                        features = listOf(
                            "Unlimited Part Practices",
                            "5 Full Mock Exams / Month",
                            "6-Month History Retention"
                        ),
                        onPayWithPayme = {
                            // Call the new function to start the web flow
                            viewModel.createWebPayment(activity, "payme", "silver_monthly")
                        },
                        onPayWithClick = {
                            viewModel.createWebPayment(activity, "click", "silver_monthly")
                        },
                        onPayWithGoogle = {
                            // Google Play flow is different and would use its own logic
                            // For now, this is a placeholder.
                        }
                    )
                }
                item {
                    SubscriptionTierCard(
                        tierName = "Gold",
                        price = "50,000 UZS / month",
                        features = listOf(
                            "Everything in Silver",
                            "Unlimited Full Mock Exams",
                            "Unlimited History Retention"
                        ),
                        onPayWithPayme = {
                            // Call the new function to start the web flow
                            viewModel.createWebPayment(activity, "payme", "silver_monthly")
                        },
                        onPayWithClick = {
                            viewModel.createWebPayment(activity, "click", "silver_monthly")
                        },
                        onPayWithGoogle = {
                            // Google Play flow is different and would use its own logic
                            // For now, this is a placeholder.
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SubscriptionTierCard(
    tierName: String,
    price: String,
    features: List<String>,
    onPayWithPayme: () -> Unit,
    onPayWithClick: () -> Unit,
    onPayWithGoogle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(tierName, style = MaterialTheme.typography.headlineMedium)
            Text(
                price,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            features.forEach { feature ->
                Text("• $feature", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Payment Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPayWithPayme,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pay with Payme") }
                Button(
                    onClick = onPayWithClick,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pay with Click") }
                Button(
                    onClick = onPayWithGoogle,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pay with Google Play") }
            }
        }
    }
}