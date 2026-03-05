package com.dbeagle.theme

import com.dbeagle.settings.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for application theme state.
 * Owns dark mode preference and persistence.
 */
class ThemeManager {
    private val _darkMode = MutableStateFlow(AppPreferences.load().darkMode ?: false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        val currentSettings = AppPreferences.load()
        AppPreferences.save(currentSettings.copy(darkMode = enabled))
    }
}
