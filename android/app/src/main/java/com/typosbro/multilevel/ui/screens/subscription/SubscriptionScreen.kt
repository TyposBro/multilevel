package com.typosbro.multilevel.ui.screens.subscription

import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                            // In a real app, you'd launch the Payme SDK here.
                            // The SDK would return a transaction ID.
                            // For simulation, we use a fake one.
                            viewModel.verifyPurchase("payme", "fake_payme_trans", "silver_monthly")
                        },
                        onPayWithClick = {
                            viewModel.verifyPurchase("click", "fake_click_trans", "silver_monthly")
                        },
                        onPayWithGoogle = {
                            viewModel.verifyPurchase(
                                "google",
                                "fake_google_token",
                                "silver_monthly"
                            )
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
                            viewModel.verifyPurchase("payme", "fake_payme_trans", "gold_monthly")
                        },
                        onPayWithClick = {
                            viewModel.verifyPurchase("click", "fake_click_trans", "gold_monthly")
                        },
                        onPayWithGoogle = {
                            viewModel.verifyPurchase("google", "fake_google_token", "gold_monthly")
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