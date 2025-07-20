// android/app/src/main/java/com/typosbro/multilevel/ui/screens/subscription/SubscriptionScreen.kt

package org.milliytechnology.spiko.ui.screens.subscription

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
import androidx.compose.material3.OutlinedButton
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
import com.android.billingclient.api.ProductDetails
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

    LaunchedEffect(Unit) {
        viewModel.loadProducts()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("SubscriptionScreen", "App Resumed. Checking for pending web payments.")
                // TODO: Logic to check for and verify pending web-based purchases
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                    val silverDetails =
                        uiState.productDetails.find { it.productId == "silver_monthly" }
                    SubscriptionTierCard(
                        tierName = "Silver",
                        productDetails = silverDetails,
                        features = listOf(
                            "Unlimited Part Practices",
                            "5 Full Mock Exams / Month",
                            "6-Month History Retention"
                        ),
                        onPayWithGoogle = {
                            if (silverDetails != null) {
                                viewModel.launchGooglePlayPurchase(activity, silverDetails)
                            } else {
                                Toast.makeText(context, "Plan not available", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        onPayWithLocalProvider = {
                            viewModel.createWebPayment(activity, "payme", "silver_monthly")
                        }
                    )
                }
                item {
                    val goldDetails = uiState.productDetails.find { it.productId == "gold_monthly" }
                    SubscriptionTierCard(
                        tierName = "Gold",
                        productDetails = goldDetails,
                        features = listOf(
                            "Everything in Silver",
                            "Unlimited Full Mock Exams",
                            "Unlimited History Retention"
                        ),
                        onPayWithGoogle = {
                            if (goldDetails != null) {
                                viewModel.launchGooglePlayPurchase(activity, goldDetails)
                            } else {
                                Toast.makeText(context, "Plan not available", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        onPayWithLocalProvider = {
                            viewModel.createWebPayment(activity, "payme", "gold_monthly")
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
    productDetails: ProductDetails?,
    features: List<String>,
    onPayWithGoogle: () -> Unit,
    onPayWithLocalProvider: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(tierName, style = MaterialTheme.typography.headlineMedium)
            Text(
                // Display localized price from Google Play, or a placeholder if still loading.
                productDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: "Loading price...",
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
                    onClick = onPayWithGoogle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = productDetails != null // Button is disabled until details are loaded
                ) {
                    Text("Subscribe with Google Play")
                }
                OutlinedButton(
                    onClick = onPayWithLocalProvider,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Other Payment Methods")
                }
            }
        }
    }
}