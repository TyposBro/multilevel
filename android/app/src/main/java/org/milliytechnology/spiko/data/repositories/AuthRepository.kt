// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/AuthRepository.kt
package org.milliytechnology.spiko.data.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.milliytechnology.spiko.data.remote.ApiService
import org.milliytechnology.spiko.data.remote.models.AuthResponse
import org.milliytechnology.spiko.data.remote.models.GenericSuccessResponse
import org.milliytechnology.spiko.data.remote.models.GoogleSignInRequest
import org.milliytechnology.spiko.data.remote.models.OneTimeTokenRequest
import org.milliytechnology.spiko.data.remote.models.RepositoryResult
import org.milliytechnology.spiko.data.remote.models.ReviewerLoginRequest
import org.milliytechnology.spiko.data.remote.models.UserProfileResponse
import org.milliytechnology.spiko.data.remote.models.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

// --- Auth Repository ---
@Singleton // Make the repository a singleton
class AuthRepository @Inject constructor( // <-- ADD @Inject constructor
    private val apiService: ApiService
) {

    suspend fun googleSignIn(request: GoogleSignInRequest): RepositoryResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { apiService.googleSignIn(request) }
        }

    suspend fun verifyTelegramToken(request: OneTimeTokenRequest): RepositoryResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { apiService.verifyTelegramToken(request) }
        }

    suspend fun reviewerLogin(request: ReviewerLoginRequest): RepositoryResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { apiService.reviewerLogin(request) }
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