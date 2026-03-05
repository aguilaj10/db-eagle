package com.dbeagle.theme

import com.dbeagle.settings.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for application theme state.
 * Owns dark mode preference and persistence.
 *
 * darkModeOverride: null means "use system preference", non-null is user's explicit choice.
 */
class ThemeManager {
    private val _darkModeOverride = MutableStateFlow(AppPreferences.load().darkMode)
    val darkModeOverride: StateFlow<Boolean?> = _darkModeOverride.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _darkModeOverride.value = enabled
        val currentSettings = AppPreferences.load()
        AppPreferences.save(currentSettings.copy(darkMode = enabled))
    }
}
