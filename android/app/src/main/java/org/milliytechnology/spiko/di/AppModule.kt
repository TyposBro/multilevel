// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/di/AppModule.kt

package org.milliytechnology.spiko.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.milliytechnology.spiko.data.local.AppDatabase
import org.milliytechnology.spiko.data.local.ExamResultDao
import org.milliytechnology.spiko.data.local.WordDao
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }


    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideWordDao(database: AppDatabase): WordDao {
        return database.wordDao()
    }


    @Provides
    @Singleton
    fun provideExamResultDao(database: AppDatabase): ExamResultDao {
        return database.multilevelExamResultDao()
    }


}