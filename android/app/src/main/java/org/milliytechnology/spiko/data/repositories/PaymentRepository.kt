package org.milliytechnology.spiko.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import org.milliytechnology.spiko.data.remote.models.CreatePaymentRequest // Create this
import org.milliytechnology.spiko.data.remote.models.CreatePaymentResponse // Create this
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val apiService: ApiService
) {
    /**
     * Calls the backend to create a payment receipt with a specific provider.
     */
    suspend fun createPayment(
        provider: String,
        planId: String
    ): RepositoryResult<CreatePaymentResponse> {
        val request = CreatePaymentRequest(provider, planId)
        return safeApiCall { apiService.createPayment(request) }
    }
}