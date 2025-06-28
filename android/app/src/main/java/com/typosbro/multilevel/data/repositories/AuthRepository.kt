// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/AuthRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.AuthResponse
import com.typosbro.multilevel.data.remote.models.GenericSuccessResponse
import com.typosbro.multilevel.data.remote.models.GoogleSignInRequest
import com.typosbro.multilevel.data.remote.models.OneTimeTokenRequest
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.UserProfileResponse
import com.typosbro.multilevel.data.remote.models.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Auth Repository ---
class AuthRepository(private val apiService: ApiService) {


    suspend fun googleSignIn(request: GoogleSignInRequest): RepositoryResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { apiService.googleSignIn(request) }
        }

    suspend fun verifyTelegramToken(request: OneTimeTokenRequest): RepositoryResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { apiService.verifyTelegramToken(request) }
        }

    suspend fun getUserProfile(): RepositoryResult<UserProfileResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { apiService.getProfile() }
        }

    suspend fun deleteUserProfile(): RepositoryResult<GenericSuccessResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { apiService.deleteProfile() }
        }
}