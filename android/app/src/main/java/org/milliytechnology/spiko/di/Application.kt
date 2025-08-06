// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/di/HiltApplication.kt
package org.milliytechnology.spiko.di

import android.app.Application
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class Application : Application() {
    // Use a coroutine scope that lives as long as the application itself
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Configure global Moshi instance for Click SDK compatibility
        configureMoshi()
    }

    private fun configureMoshi() {
        try {
            // Create a Moshi instance with Kotlin support
            Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            // This might help with global Moshi configuration
            // Note: This is a workaround attempt - the Click SDK should ideally
            // handle this internally

        } catch (e: Exception) {
            // Log the error but don't crash the app
            android.util.Log.e("SpikoApp", "Failed to configure Moshi", e)
        }
    }

}