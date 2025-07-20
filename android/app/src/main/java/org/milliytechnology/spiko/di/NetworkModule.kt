// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/di/NetworkModule.kt
package org.milliytechnology.spiko.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.milliytechnology.spiko.data.local.SessionManager
import org.milliytechnology.spiko.data.remote.ApiService
import org.milliytechnology.spiko.data.remote.interceptors.AuthInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://typosbro-multilevel-api.milliytechnology.workers.dev/api/"

    // This is correct: It depends on SessionManager, which AppModule provides.
    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): AuthInterceptor {
        return AuthInterceptor(sessionManager)
    }

    // Your named OkHttpClients are correct. They now depend on the Hilt-provided interceptor.
    @Provides
    @Singleton
    @Named("StandardOkHttpClient")
    fun provideStandardOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // This one is generic (no @Named annotation)
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // No need for AuthInterceptor or heavy logging for asset downloads
            .connectTimeout(60, TimeUnit.SECONDS) // Longer timeout for large files
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(@Named("StandardOkHttpClient") okHttpClient: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}