package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.preferences.LanguageRepository
import com.typosbro.multilevel.data.preferences.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val languageRepository: LanguageRepository // Inject the new repository
) : ViewModel() {

    // Expose the theme preference as a StateFlow
    val isDarkTheme = themeRepository.isDarkTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Expose the language preference as a StateFlow
    val currentLanguageCode: StateFlow<String?> = languageRepository.languageCode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null // The initial value is null, representing "not loaded yet"
        )

    /**
     * Called by the UI when the theme switch is toggled.
     */
    fun onThemeChanged(isDark: Boolean) {
        viewModelScope.launch {
            themeRepository.setTheme(isDark)
        }
    }

    /**
     * Called by the UI when a new language is selected.
     */
    fun onLanguageChanged(languageCode: String) {
        viewModelScope.launch {
            languageRepository.setLanguage(languageCode)
        }
    }
}