package org.milliytechnology.spiko.data.repositories


import org.milliytechnology.spiko.data.remote.ApiService
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.remote.models.SubscriptionResponse // Create this
import org.milliytechnology.spiko.data.remote.models.VerifyPurchaseRequest // Create this
import org.milliytechnology.spiko.data.remote.models.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun verifyPurchase(
        provider: String,
        token: String,
        planId: String
    ): RepositoryResult<SubscriptionResponse> {
        val request = VerifyPurchaseRequest(provider, token, planId)
        return safeApiCall { apiService.verifyPurchase(request) }
    }
}