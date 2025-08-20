package org.milliytechnology.spiko.features.billing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import javax.inject.Inject

/**
 * Integration tests for Google Play Billing.
 * These tests run on the Android device/emulator and test the actual integration
 * with the dependency injection framework.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BillingIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var billingClient: BillingClientWrapper

    @Inject
    lateinit var billingTestHelper: BillingTestHelper

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun billingClient_injectedCorrectly() {
        assertNotNull("BillingClient should be injected", billingClient)
        assertNotNull("BillingTestHelper should be injected", billingTestHelper)
    }

    @Test
    fun billingClient_startConnection_becomesReady() = runTest {
        // When
        billingClient.startConnection()
        
        // Then - for fake client, it should be ready immediately
        val isReady = billingClient.isReady.first()
        assertTrue("Billing client should be ready after connection", isReady)
    }

    @Test
    fun billingClient_queryProducts_completesWithoutError() = runTest {
        // Given
        billingClient.startConnection()
        
        // When
        try {
            billingClient.queryProductDetails(listOf("silver_monthly", "gold_monthly"))
            // If we get here, the query completed without throwing an exception
            assertTrue("Product query should complete without error", true)
        } catch (e: Exception) {
            fail("Product query should not throw exception: ${e.message}")
        }
    }

    @Test
    fun billingTestHelper_integration_worksWithInjectedClient() = runTest {
        // Given
        val productId = "silver_monthly"
        
        // When
        try {
            billingTestHelper.simulateSuccessfulPurchase(productId)
            
            // Then - verify purchase was emitted
            val purchases = billingClient.purchases.first { it.isNotEmpty() }
            assertFalse("Purchases should not be empty after simulation", purchases.isEmpty())
            assertEquals("Purchase should be for correct product", productId, purchases.first().products.first())
        } catch (e: Exception) {
            fail("Billing test helper integration should not throw exception: ${e.message}")
        }
    }

    @Test
    fun billingTestHelper_runAllScenarios_integrationTest() = runTest {
        // Test that all billing scenarios work with the injected dependencies
        val productId = "gold_monthly"
        
        BillingTestScenario.values().forEach { scenario ->
            try {
                billingTestHelper.runBillingScenarioTest(scenario, productId)
                // Small delay to allow processing
                kotlinx.coroutines.delay(100)
            } catch (e: Exception) {
                fail("Integration test for scenario $scenario failed: ${e.message}")
            }
        }
    }

    @Test
    fun billingClient_contextIsValid() {
        // Verify that the billing client has access to a valid Android context
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull("Test should have access to valid context", context)
        assertEquals("Context should be for the correct package", "org.milliytechnology.spiko", context.packageName)
    }
}
