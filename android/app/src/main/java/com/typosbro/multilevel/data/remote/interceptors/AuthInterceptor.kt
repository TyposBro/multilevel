// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/interceptors/AuthInterceptor.kt
package com.typosbro.multilevel.data.remote.interceptors

import com.typosbro.multilevel.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // --- THIS IS THE CORRECTED LINE ---
        // Get the current token directly from the StateFlow's value property.
        val token = sessionManager.tokenFlow.value

        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Define which routes are "public" and should NOT receive a token.
        val isPublicRoute = path.contains("/auth/login") || path.contains("/auth/register")

        val requestBuilder = originalRequest.newBuilder()

        // If a token exists AND it's NOT a public route, add the Authorization header.
        if (token != null && !isPublicRoute) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        val response = chain.proceed(request)

        // If the server rejects our token with 401, trigger a global logout.
        // This check should only matter for non-public routes that we tried to authorize.
        if (response.code == 401 && !isPublicRoute) {
            runBlocking {
                sessionManager.logout()
            }
        }

        return response
    }
}