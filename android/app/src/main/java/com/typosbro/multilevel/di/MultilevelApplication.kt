// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/di/HiltApplication.kt
package com.typosbro.multilevel.di

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MultilevelApplication : Application() {
    // You can add onCreate logic here if needed later
}