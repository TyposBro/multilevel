// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/AuthRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.*
import com.typosbro.multilevel.data.remote.models.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Auth Repository ---
class AuthRepository(private val apiService: ApiService) {
    suspend fun login(request: AuthRequest): RepositoryResult<AuthResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.login(request) }
    }

    suspend fun register(request: AuthRequest): RepositoryResult<AuthResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.register(request) }
    }

    suspend fun getUserProfile(): RepositoryResult<UserProfileResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.getProfile() }
    }
}