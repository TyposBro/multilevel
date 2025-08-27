package org.milliytechnology.spiko.ui.screens.subscription

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.ProductDetails
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.viewmodels.SubscriptionViewModel
import org.milliytechnology.spiko.ui.viewmodels.UserProfileViewData
import org.milliytechnology.spiko.utils.openUrlInCustomTab
import java.text.SimpleDateFormat
import java.util.*

private data class SubscriptionPlan(
    val tierNameResId: Int,
    val productId: String,
    val tierIdentifier: String, // "silver" or "gold"
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

    val subscriptionPlans = remember {
        listOf(
            SubscriptionPlan(
                tierNameResId = R.string.tier_silver,
                productId = "silver_monthly",
                tierIdentifier = "silver",
                featureResIds = listOf(
                    R.string.feature_unlimited_part_practices,
                    R.string.feature_5_mock_exams_month,
                    R.string.feature_6_month_history_retention
                )
            ),
            SubscriptionPlan(
                tierNameResId = R.string.tier_gold,
                productId = "gold_monthly",
                tierIdentifier = "gold",
                featureResIds = listOf(
                    R.string.feature_everything_in_silver,
                    R.string.feature_unlimited_full_mock_exams,
                    R.string.feature_unlimited_history_retention
                )
            )
        )
    }

    // Effect to launch browser for Click/Payme payments
    LaunchedEffect(uiState.paymentUrlToLaunch) {
        uiState.paymentUrlToLaunch?.let { url ->
            openUrlInCustomTab(context, url)
            viewModel.clearPaymentUrl()
        }
    }

    // Effect to show success/error toasts and refresh the user's profile
    LaunchedEffect(uiState.purchaseSuccessMessage, uiState.error) {
        uiState.purchaseSuccessMessage?.let {
            viewModel.onPurchaseCompleted()
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
        uiState.error?.let {
            Toast.makeText(context, context.getString(R.string.error_generic_message, it), Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.button_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            val userProfile = uiState.userProfile
            val currentTier = userProfile?.subscriptionTier?.lowercase()

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Main UI Logic ---
                if (currentTier == "gold" && userProfile != null) {
                    // 1. User is Gold: Show only their Gold status card.
                    item {
                        SubscriptionStatusCard(profile = userProfile)
                    }
                } else {
                    // 2. User is Silver or Free: Show all relevant plan cards.
                    items(subscriptionPlans) { plan ->
                        val productDetails = uiState.productDetails.find { it.productId == plan.productId }

                        if (currentTier == plan.tierIdentifier && userProfile != null) {
                            // If this card matches the user's current plan (e.g., Silver), show the status card.
                            SubscriptionStatusCard(profile = userProfile)
                        } else {
                            // Otherwise, show the purchase option card.
                            val isButtonEnabled = when {
                                currentTier == "free" -> true // Can buy any plan
                                currentTier == "silver" && plan.tierIdentifier == "gold" -> true // Can upgrade to Gold
                                userProfile?.isRenewalAllowed == true -> true // Can renew
                                else -> false
                            }

                            SubscriptionTierCard(
                                plan = plan,
                                productDetails = productDetails,
                                isEnabled = isButtonEnabled,
                                onPayWithGoogle = {
                                    if (productDetails != null) {
                                        viewModel.launchGooglePlayPurchase(context as Activity, productDetails)
                                    }
                                },
                                onPayWithClick = { viewModel.createClickPayment(plan.productId) }
                            )
                        }
                    }
                }
            }

            // Global loading spinner for the whole screen
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * A prominent card that displays the user's current subscription status and expiration date.
 * Its color changes if the subscription is expiring soon.
 */
@Composable
private fun SubscriptionStatusCard(profile: UserProfileViewData) {
    val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
    val isExpiringSoon = profile.isRenewalAllowed

    val containerColor = if (isExpiringSoon) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val onContainerColor = if (isExpiringSoon) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your plan: ${profile.subscriptionTier}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onContainerColor
            )
            Spacer(Modifier.height(4.dp))
            profile.subscriptionExpiresAt?.let {
                Text(
                    "Expires on: ${dateFormatter.format(it)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onContainerColor
                )
            }
        }
    }
}

/**
 * A card that displays a purchasable subscription plan's details and payment buttons.
 */
@Composable
private fun SubscriptionTierCard(
    plan: SubscriptionPlan,
    productDetails: ProductDetails?,
    isEnabled: Boolean,
    onPayWithGoogle: () -> Unit,
    onPayWithClick: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                isGooglePayEnabled = productDetails != null && isEnabled,
                isClickPayEnabled = isEnabled
            )
        }
    }
}

/**
 * A single row showing a feature with a checkmark icon.
 */
@Composable
private fun FeatureRow(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A component that groups the payment buttons together.
 */
@Composable
private fun PaymentButtons(
    onPayWithGoogle: () -> Unit,
    onPayWithClick: () -> Unit,
    isGooglePayEnabled: Boolean,
    isClickPayEnabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPayWithGoogle,
            modifier = Modifier.fillMaxWidth(),
            enabled = isGooglePayEnabled
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google_play_logo),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
                tint = Color.Unspecified
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.subscription_google_play_button))
        }
        Button(
            onClick = onPayWithClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = isClickPayEnabled
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_click_logo),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
                tint = Color.Unspecified
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.subscription_click_button))
        }
    }
}