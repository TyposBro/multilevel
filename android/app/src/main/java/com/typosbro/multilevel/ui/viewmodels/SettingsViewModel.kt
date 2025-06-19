package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.preferences.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    // Expose the theme preference as a StateFlow for the UI to collect
    val isDarkTheme = themeRepository.isDarkTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false // Initial value before the flow emits
        )

    /**
     * Called by the UI when the theme switch is toggled.
     */
    fun onThemeChanged(isDark: Boolean) {
        viewModelScope.launch {
            themeRepository.setTheme(isDark)
        }
    }
}