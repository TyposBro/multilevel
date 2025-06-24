package com.typosbro.multilevel.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.data.local.WordDao
import com.typosbro.multilevel.data.local.WordDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// --- ADD THIS TOP-LEVEL DELEGATE ---
// Define the DataStore file name and the delegate once, in a central place.
private const val SETTINGS_PREFERENCES = "settings_prefs"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_PREFERENCES)
// ------------------------------------


@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // --- ADD THIS NEW PROVIDER ---
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
    // -----------------------------

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideWordDatabase(@ApplicationContext context: Context): WordDatabase {
        return WordDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideWordDao(database: WordDatabase): WordDao {
        return database.wordDao()
    }

    @Provides
    @Singleton
    fun provideSessionManager(tokenManager: TokenManager): SessionManager {
        return SessionManager(tokenManager)
    }
}