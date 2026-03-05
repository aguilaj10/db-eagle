package com.dbeagle.settings

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences

/**
 * Provides Settings instances backed by java.util.prefs.Preferences.
 * Uses PreferencesSettings from multiplatform-settings for type-safe access and Flow support.
 *
 * Node paths match existing Preferences locations to maintain compatibility:
 * - App settings: com.dbeagle.settings
 * - Profiles: com.dbeagle.profiles
 */
object SettingsProvider {
    /**
     * Settings for application preferences.
     * Node: com.dbeagle.settings
     */
    fun createAppSettings(): ObservableSettings {
        val delegate = Preferences.userRoot().node("com.dbeagle.settings")
        return PreferencesSettings(delegate)
    }

    /**
     * Settings for connection profiles.
     * Node: com.dbeagle.profiles
     */
    fun createProfileSettings(): ObservableSettings {
        val delegate = Preferences.userRoot().node("com.dbeagle.profiles")
        return PreferencesSettings(delegate)
    }
}
