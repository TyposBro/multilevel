package com.typosbro.multilevel.data.remote.models

import org.json.JSONObject
import retrofit2.Response

/**
 * A reusable sealed class for wrapping repository responses.
 * It clearly defines a success state with data or an error state with a message.
 */
sealed class RepositoryResult<out T> {
    data class Success<out T>(val data: T) : RepositoryResult<T>()
    data class Error(val message: String, val code: Int? = null) : RepositoryResult<Nothing>()
}

/**
 * A reusable helper function to safely execute API calls and wrap the response
 * in our custom RepositoryResult class. It handles success, API errors, and
 * network/parsing exceptions.
 */
suspend inline fun <T> safeApiCall(crossinline apiCall: suspend () -> Response<T>): RepositoryResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            response.body()?.let { RepositoryResult.Success(it) }
                ?: RepositoryResult.Error("API call successful but response body was null", response.code())
        } else {
            // Try to parse the specific error message from the backend
            val errorBody = response.errorBody()?.string()
            val errorMessage = try {
                errorBody?.let { JSONObject(it).getString("message") } ?: "Unknown error"
            } catch (e: Exception) {
                response.message() ?: "Unknown error" // Fallback to HTTP message
            }
            RepositoryResult.Error(errorMessage, response.code())
        }
    } catch (e: Exception) {
        RepositoryResult.Error("Network error or exception: ${e.message ?: "Unknown exception"}")
    }
}