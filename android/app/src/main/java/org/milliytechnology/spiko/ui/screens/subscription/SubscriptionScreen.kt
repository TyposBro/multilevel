// android/app/src/main/java/org/milliytechnology/spiko/ui/screens/subscription/SubscriptionScreen.kt

package org.milliytechnology.spiko.ui.screens.subscription

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.ProductDetails
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.viewmodels.SubscriptionViewModel
import org.milliytechnology.spiko.utils.openUrlInCustomTab

private data class SubscriptionPlan(
    val tierNameResId: Int,
    val productId: String,
    val featureResIds: List<Int>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val subscriptionPlans = remember {
        listOf(
            SubscriptionPlan(
                tierNameResId = R.string.tier_silver,
                productId = "silver_monthly",
                featureResIds = listOf(
                    R.string.feature_unlimited_part_practices,
                    R.string.feature_5_mock_exams_month,
                    R.string.feature_6_month_history_retention
                )
            ),
            SubscriptionPlan(
                tierNameResId = R.string.tier_gold,
                productId = "gold_monthly",
                featureResIds = listOf(
                    R.string.feature_everything_in_silver,
                    R.string.feature_unlimited_full_mock_exams,
                    R.string.feature_unlimited_history_retention
                )
            )
        )
    }

    // This effect listens for when the app comes back to the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("SubscriptionScreen", "App Resumed. Checking for pending web payments.")
                // TODO: Here you would add logic to check the status of a pending
                // web payment with your backend if needed.
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // This effect will launch the browser when a payment URL is available.
    LaunchedEffect(uiState.paymentUrlToLaunch) {
        uiState.paymentUrlToLaunch?.let { url ->
            openUrlInCustomTab(context, url)
            viewModel.clearPaymentUrl() // Reset the state to prevent re-launching on config change
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
            Toast.makeText(
                context,
                context.getString(R.string.error_generic_message, it),
                Toast.LENGTH_LONG
            ).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_title)) },
                navigationIcon = {
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
                items(subscriptionPlans) { plan ->
                    val productDetails =
                        uiState.productDetails.find { it.productId == plan.productId }

                    SubscriptionTierCard(
                        plan = plan,
                        productDetails = productDetails,
                        onPayWithGoogle = {
                            if (productDetails != null) {
                                // The Activity is now correctly cast only where it's used
                                viewModel.launchGooglePlayPurchase(
                                    context as Activity,
                                    productDetails
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.plan_not_available),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onPayWithClick = {
                            viewModel.createClickPayment(plan.productId)
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
    plan: SubscriptionPlan,
    productDetails: ProductDetails?,
    onPayWithGoogle: () -> Unit,
    onPayWithClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(plan.tierNameResId),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = productDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: stringResource(R.string.subscription_loading_price),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            plan.featureResIds.forEach { featureResId ->
                FeatureRow(text = stringResource(featureResId))
            }
            Spacer(modifier = Modifier.height(24.dp))

            PaymentButtons(
                onPayWithGoogle = onPayWithGoogle,
                onPayWithClick = onPayWithClick,
                isGooglePayEnabled = productDetails != null
            )
        }
    }
}

@Composable
private fun FeatureRow(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null, // Decorative
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PaymentButtons(
    onPayWithGoogle: () -> Unit,
    onPayWithClick: () -> Unit,
    isGooglePayEnabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPayWithGoogle,
            modifier = Modifier.fillMaxWidth(),
            enabled = isGooglePayEnabled
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google_play_logo),
                contentDescription = null, // Decorative
                modifier = Modifier.size(ButtonDefaults.IconSize),
                tint = Color.Unspecified
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.subscription_google_play_button))
        }
//        Button(
//            onClick = onPayWithClick,
//            modifier = Modifier.fillMaxWidth(),
//        ) {
//            Icon(
//                painter = painterResource(id = R.drawable.ic_click_logo),
//                contentDescription = null, // Decorative
//                modifier = Modifier.size(ButtonDefaults.IconSize),
//                tint = Color.Unspecified
//            )
//            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
//            Text(stringResource(R.string.subscription_click_button))
//        }
    }
}