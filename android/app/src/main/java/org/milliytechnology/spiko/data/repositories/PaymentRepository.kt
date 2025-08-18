package org.milliytechnology.spiko.data.repositories

import org.milliytechnology.spiko.data.remote.ApiService
import org.milliytechnology.spiko.data.remote.models.CreatePaymentRequest
import org.milliytechnology.spiko.data.remote.models.CreatePaymentResponse
import org.milliytechnology.spiko.data.remote.models.PaymentStatusResponse
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.remote.models.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val apiService: ApiService
) {
    /**
     * Calls the backend to create a payment receipt with a specific provider.
     * Legacy method for backward compatibility.
     */
    suspend fun createPayment(
        provider: String,
        planId: String
    ): RepositoryResult<CreatePaymentResponse> {
        val request = CreatePaymentRequest(provider, planId)
        return safeApiCall { apiService.createPayment(request) }
    }

    /**
     * Enhanced method to create payment with additional parameters for Click
     */
    suspend fun createPayment(request: CreatePaymentRequest): RepositoryResult<CreatePaymentResponse> {
        return safeApiCall { apiService.createPayment(request) }
    }

    /**
     * Checks the status of a payment transaction
     */
    suspend fun getPaymentStatus(transactionId: String): RepositoryResult<PaymentStatusResponse> {
        return safeApiCall { apiService.getPaymentStatus(transactionId) }
    }
}