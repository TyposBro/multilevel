// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/di/HiltApplication.kt
package com.typosbro.multilevel.di

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class MultilevelApplication : Application() {
    // Use a coroutine scope that lives as long as the application itself
    private val applicationScope = CoroutineScope(Dispatchers.Default)

}