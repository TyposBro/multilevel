// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/AuthRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response // Import Retrofit Response

// Simple Result wrapper for handling success/failure
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
}

// Helper function to handle API calls safely
suspend inline fun <T> safeApiCall(crossinline apiCall: suspend () -> Response<T>): Result<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            response.body()?.let { Result.Success(it) }
                ?: Result.Error("API call successful but response body was null", response.code())
        } else {
            // Try parsing backend error message if possible
            val errorBody = response.errorBody()?.string()
            val errorMessage = try {
                errorBody?.let { org.json.JSONObject(it).getString("message") } ?: "Unknown error"
            } catch (e: Exception) {
                response.message() ?: "Unknown error" // Fallback to HTTP message
            }
            Result.Error(errorMessage, response.code())
        }
    } catch (e: Exception) {
        Result.Error("Network error or exception: ${e.message ?: "Unknown exception"}")
    }
}


// --- Auth Repository ---
class AuthRepository(private val apiService: ApiService) {
    suspend fun login(request: AuthRequest): Result<AuthResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.login(request) }
    }

    suspend fun register(request: AuthRequest): Result<AuthResponse> = withContext(Dispatchers.IO) {
        safeApiCall { apiService.register(request) }
    }
}