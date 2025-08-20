package org.milliytechnology.spiko.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.features.billing.BillingClientWrapper
import org.milliytechnology.spiko.features.billing.BillingTestHelper
import org.milliytechnology.spiko.features.billing.BillingTestScenario
import javax.inject.Inject

data class BillingDebugUiState(
    val isBillingReady: Boolean = false,
    val productDetailsCount: Int = 0,
    val lastPurchaseInfo: String? = null,
    val isLoading: Boolean = false,
    val logs: List<String> = emptyList()
)

@HiltViewModel
class BillingDebugViewModel @Inject constructor(
    private val billingClient: BillingClientWrapper,
    private val billingTestHelper: BillingTestHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingDebugUiState())
    val uiState = _uiState.asStateFlow()

    private val logs = mutableListOf<String>()

    init {
        observeBillingState()
        addLog("BillingDebugViewModel initialized")
    }

    private fun observeBillingState() {
        // Observe billing client ready state
        billingClient.isReady.onEach { isReady ->
            _uiState.update { it.copy(isBillingReady = isReady) }
            addLog("Billing client ready state: $isReady")
        }.launchIn(viewModelScope)

        // Observe product details
        billingClient.productDetails.onEach { products ->
            _uiState.update { it.copy(productDetailsCount = products.size) }
            addLog("Product details updated: ${products.size} products")
        }.launchIn(viewModelScope)

        // Observe purchases
        billingClient.purchases.onEach { purchases ->
            val purchaseInfo = if (purchases.isNotEmpty()) {
                val lastPurchase = purchases.last()
                "Token: ${lastPurchase.purchaseToken.take(10)}..., Products: ${lastPurchase.products}"
            } else {
                null
            }
            _uiState.update { it.copy(lastPurchaseInfo = purchaseInfo) }
            addLog("Purchase update: ${purchases.size} purchases")
        }.launchIn(viewModelScope)
    }

    fun runTestScenario(scenario: BillingTestScenario, productId: String) {
        addLog("Running test scenario: ${scenario.name} for product: $productId")
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                billingTestHelper.runBillingScenarioTest(scenario, productId)
                addLog("Test scenario completed successfully")
            } catch (e: Exception) {
                addLog("Test scenario failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun queryProducts() {
        addLog("Querying product details...")
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                val productIds = listOf("silver_monthly", "gold_monthly")
                billingClient.queryProductDetails(productIds)
                addLog("Product query initiated for: ${productIds.joinToString()}")
            } catch (e: Exception) {
                addLog("Product query failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun resetBillingState() {
        addLog("Resetting billing state...")
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                billingTestHelper.resetBillingState()
                addLog("Billing state reset completed")
            } catch (e: Exception) {
                addLog("Billing state reset failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[${timestamp % 100000}] $message"
        logs.add(logMessage)
        
        // Keep only last 50 logs
        if (logs.size > 50) {
            logs.removeAt(0)
        }
        
        _uiState.update { it.copy(logs = logs.toList()) }
    }

    override fun onCleared() {
        super.onCleared()
        addLog("BillingDebugViewModel cleared")
    }
}
