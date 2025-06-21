package com.typosbro.multilevel.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
// import java.util.Locale // No longer needed here
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val selectedLanguageKey = stringPreferencesKey("selected_language_code")

    /**
     * A Flow that emits the current language code (e.g., "en", "ru")
     * or null if no preference has been set yet.
     */
    val languageCode: Flow<String?> = dataStore.data // Change to Flow<String?>
        .map { preferences ->
            // This will emit the saved code, or null if the key doesn't exist.
            preferences[selectedLanguageKey]
        }

    suspend fun setLanguage(languageCode: String) {
        dataStore.edit { settings ->
            settings[selectedLanguageKey] = languageCode
        }
    }
}