// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/remote/interceptors/AuthInterceptor.kt
package com.typosbro.multilevel.data.remote.interceptors

import com.typosbro.multilevel.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getToken()
        val originalRequest = chain.request()

        val requestBuilder = originalRequest.newBuilder()
        if (token != null && !originalRequest.url.encodedPath.contains("/auth/")) { // Don't add token to auth routes
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}