// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/di/HiltApplication.kt
package com.typosbro.multilevel.di

import android.app.Application
import com.typosbro.multilevel.features.inference.OnnxRuntimeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class MultilevelApplication : Application() {
    // Use a coroutine scope that lives as long as the application itself
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        
        // Launch the initialization on a background thread so it doesn't block
        // the main thread during app startup.
        applicationScope.launch {
            OnnxRuntimeManager.initialize(this@MultilevelApplication)
        }
    }
}