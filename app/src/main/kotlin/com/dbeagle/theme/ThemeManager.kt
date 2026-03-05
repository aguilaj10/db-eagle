package com.dbeagle.theme

import com.dbeagle.settings.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton manager for application theme state.
 * Owns dark mode preference and persistence.
 *
 * Reactively observes dark mode setting from AppPreferencesRepository.
 * darkModeOverride: null means "use system preference", non-null is user's explicit choice.
 */
class ThemeManager(
    private val appPreferencesRepository: AppPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _darkModeOverride = MutableStateFlow<Boolean?>(null)
    val darkModeOverride: StateFlow<Boolean?> = _darkModeOverride.asStateFlow()

    init {
        scope.launch {
            appPreferencesRepository.darkModeFlow.collect { darkMode ->
                _darkModeOverride.value = darkMode
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        _darkModeOverride.value = enabled
        scope.launch {
            appPreferencesRepository.setDarkMode(enabled)
        }
    }
}
