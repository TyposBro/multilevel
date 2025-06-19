package com.typosbro.multilevel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Define a top-level property delegate to create the DataStore instance.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Define the key for our dark mode preference.
    private val isDarkThemeKey = booleanPreferencesKey("is_dark_theme")

    /**
     * A Flow that emits the current dark theme preference.
     * It will automatically emit a new value whenever the preference changes.
     * Defaults to `false` (light mode) if no preference is set.
     */
    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[isDarkThemeKey] == true // Default to light mode
        }

    /**
     * Saves the user's theme preference to DataStore.
     * @param isDark The new theme preference to save.
     */
    suspend fun setTheme(isDark: Boolean) {
        context.dataStore.edit { settings ->
            settings[isDarkThemeKey] = isDark
        }
    }
}