package com.typosbro.multilevel.data.remote

import android.content.Context
import com.google.gson.GsonBuilder
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.remote.interceptors.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Replace with your actual backend URL
    private const val BASE_URL = "http://192.168.25.19:3000/api/" // 10.0.2.2 for Android Emulator accessing localhost

    fun create(context: Context): ApiService {
        val tokenManager = TokenManager(context.applicationContext) // Use application context

        // Logging Interceptor (for debugging)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Log request/response bodies
        }

        // OkHttpClient with Auth Interceptor
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor) // Add logging last to see final request
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Gson configuration (optional, if needed)
        val gson = GsonBuilder()
            .setLenient() // If backend JSON is slightly non-standard
            .create()

        // Retrofit Instance
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(ApiService::class.java)
    }
}

// Replace BASE_URL with your actual backend server address.
// If running the backend locally and testing on an Android emulator,
// http://10.0.2.2:PORT/api/ usually works.
// If testing on a physical device on the same network,
// use your computer's local network IP address
// (e.g., http://192.168.1.100:PORT/api/).
// Ensure your backend allows connections from this IP.