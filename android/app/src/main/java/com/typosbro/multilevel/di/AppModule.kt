// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/di/AppModule.kt
package com.typosbro.multilevel.di


import android.content.Context
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.local.WordDao
import com.typosbro.multilevel.data.local.WordDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // These dependencies will live as long as the app does
object AppModule {


}