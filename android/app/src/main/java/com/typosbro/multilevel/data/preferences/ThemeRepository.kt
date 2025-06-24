package com.typosbro.multilevel.data.preferences

// import android.content.Context // NO LONGER NEEDED
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ThemeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val isDarkThemeKey = booleanPreferencesKey("is_dark_theme")

    val isDarkTheme: Flow<Boolean> = dataStore.data // Use the injected dataStore
        .map { preferences ->
            preferences[isDarkThemeKey] == true
        }

    suspend fun setTheme(isDark: Boolean) {
        dataStore.edit { settings -> // Use the injected dataStore
            settings[isDarkThemeKey] = isDark
        }
    }
}