package com.typosbro.multilevel.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val selectedLanguageKey = stringPreferencesKey("selected_language_code")

    // A set of languages your app and the backend officially support.
    private val supportedLanguages = setOf("en", "ru", "uz")

    /**
     * A Flow that emits the current language code (e.g., "en", "ru").
     * It will always emit a value: either the user's preference or a sensible default.
     */
    val languageCode: Flow<String> = dataStore.data
        .map { preferences ->
            // 1. Try to get the user's explicitly saved preference.
            val savedCode = preferences[selectedLanguageKey]

            if (savedCode != null) {
                // If a preference is saved, use it.
                savedCode
            } else {
                // 2. If no preference is saved, get the phone's system language.
                val systemLanguage = Locale.getDefault().language // e.g., "en", "ru", "uz"

                // 3. Check if the system language is one you support.
                if (systemLanguage in supportedLanguages) {
                    systemLanguage
                } else {
                    // 4. If not supported, fall back to English as a safe default.
                    "en"
                }
            }
        }

    suspend fun setLanguage(languageCode: String) {
        dataStore.edit { settings ->
            settings[selectedLanguageKey] = languageCode
        }
    }
}