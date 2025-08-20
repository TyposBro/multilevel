package org.milliytechnology.spiko.ui.screens.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.features.billing.BillingTestHelper
import org.milliytechnology.spiko.features.billing.BillingTestScenario
import org.milliytechnology.spiko.ui.viewmodels.BillingDebugViewModel

/**
 * Debug screen for testing Google Play Billing functionality.
 * Only available in debug builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingDebugScreen(
    onNavigateBack: () -> Unit,
    viewModel: BillingDebugViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing Debug") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Billing Status",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Billing Client Ready: ${uiState.isBillingReady}")
                        Text("Product Details Count: ${uiState.productDetailsCount}")
                        Text("Last Purchase: ${uiState.lastPurchaseInfo ?: "None"}")
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Test Scenarios",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Simulate different billing scenarios:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(BillingTestScenario.values()) { scenario ->
                BillingTestScenarioCard(
                    scenario = scenario,
                    onTestScenario = { productId ->
                        viewModel.runTestScenario(scenario, productId)
                    },
                    isLoading = uiState.isLoading
                )
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Manual Controls",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.queryProducts() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Text("Query Product Details")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.resetBillingState() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Text("Reset Billing State")
                        }
                    }
                }
            }

            if (uiState.logs.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Recent Logs",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            uiState.logs.takeLast(10).forEach { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillingTestScenarioCard(
    scenario: BillingTestScenario,
    onTestScenario: (String) -> Unit,
    isLoading: Boolean
) {
    var selectedProductId by remember { mutableStateOf("silver_monthly") }
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = scenario.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = selectedProductId,
                    onValueChange = { selectedProductId = it },
                    label = { Text("Product ID") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                Button(
                    onClick = { onTestScenario(selectedProductId) },
                    enabled = !isLoading && selectedProductId.isNotBlank()
                ) {
                    Text("Test")
                }
            }
        }
    }
}
