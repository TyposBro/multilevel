// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/di/NetworkModule.kt
package com.typosbro.multilevel.di

import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.RetrofitClient
import com.typosbro.multilevel.data.remote.interceptors.AuthInterceptor
import com.typosbro.multilevel.data.repositories.AuthRepository
import com.typosbro.multilevel.data.repositories.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): AuthInterceptor {
        return AuthInterceptor(sessionManager)
    }

    @Provides
    @Singleton
    @Named("StandardOkHttpClient")
    fun provideStandardOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient { // <-- Change parameter
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor) // <-- Use the injected interceptor
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("SseOkHttpClient")
    fun provideSseOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient { // <-- Change parameter
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor) // <-- Use the injected interceptor
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for SSE
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }


    @Provides
    @Singleton
    fun provideApiService(@Named("StandardOkHttpClient") okHttpClient: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl(RetrofitClient.BASE_URL)
            .client(okHttpClient) // Use the standard client for regular API calls
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(apiService: ApiService): AuthRepository {
        return AuthRepository(apiService)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        apiService: ApiService,
    ): ChatRepository = ChatRepository(apiService)
}