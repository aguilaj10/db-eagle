package com.dbeagle.settings

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getBooleanOrNullFlow
import com.russhwolf.settings.coroutines.getIntFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Repository providing reactive Flow-based access to application settings.
 * Wraps multiplatform-settings ObservableSettings with Flow API for reactive updates.
 */
@OptIn(ExperimentalSettingsApi::class)
class AppPreferencesRepository(
    private val settings: ObservableSettings,
) {
    private companion object {
        const val KEY_RESULT_LIMIT = "resultLimit"
        const val KEY_QUERY_TIMEOUT = "queryTimeoutSeconds"
        const val KEY_CONNECTION_TIMEOUT = "connectionTimeoutSeconds"
        const val KEY_MAX_CONNECTIONS = "maxConnections"
        const val KEY_DARK_MODE = "darkMode"
        const val KEY_SIDEBAR_COLLAPSED = "sidebarCollapsed"
    }

    /**
     * Flow of result limit setting.
     */
    val resultLimitFlow: Flow<Int> = settings
        .getIntFlow(KEY_RESULT_LIMIT, AppSettings.DEFAULT_RESULT_LIMIT)
        .distinctUntilChanged()

    /**
     * Flow of query timeout setting (in seconds).
     */
    val queryTimeoutFlow: Flow<Int> = settings
        .getIntFlow(KEY_QUERY_TIMEOUT, AppSettings.DEFAULT_QUERY_TIMEOUT_SECONDS)
        .distinctUntilChanged()

    /**
     * Flow of connection timeout setting (in seconds).
     */
    val connectionTimeoutFlow: Flow<Int> = settings
        .getIntFlow(KEY_CONNECTION_TIMEOUT, AppSettings.DEFAULT_CONNECTION_TIMEOUT_SECONDS)
        .distinctUntilChanged()

    /**
     * Flow of max connections setting.
     */
    val maxConnectionsFlow: Flow<Int> = settings
        .getIntFlow(KEY_MAX_CONNECTIONS, AppSettings.DEFAULT_MAX_CONNECTIONS)
        .distinctUntilChanged()

    /**
     * Flow of dark mode setting (null = system default).
     */
    val darkModeFlow: Flow<Boolean?> = settings
        .getBooleanOrNullFlow(KEY_DARK_MODE)
        .distinctUntilChanged()

    /**
     * Flow of sidebar collapsed state (false = expanded by default).
     */
    val sidebarCollapsedFlow: Flow<Boolean> = settings
        .getBooleanFlow(KEY_SIDEBAR_COLLAPSED, false)
        .distinctUntilChanged()

    /**
     * Combined flow of all settings as AppSettings data class.
     * Emits new value whenever any setting changes.
     */
    val settingsFlow: Flow<AppSettings> = combine(
        combine(
            resultLimitFlow,
            queryTimeoutFlow,
            connectionTimeoutFlow,
            maxConnectionsFlow,
        ) { resultLimit, queryTimeout, connectionTimeout, maxConnections ->
            arrayOf(resultLimit, queryTimeout, connectionTimeout, maxConnections)
        },
        darkModeFlow,
        sidebarCollapsedFlow,
    ) { timeouts: Array<out Any>, darkMode, sidebarCollapsed ->
        @Suppress("UNCHECKED_CAST")
        val arr = timeouts as Array<out Int>
        AppSettings(
            resultLimit = arr[0],
            queryTimeoutSeconds = arr[1],
            connectionTimeoutSeconds = arr[2],
            maxConnections = arr[3],
            darkMode = darkMode,
            sidebarCollapsed = sidebarCollapsed,
        )
    }

    /**
     * Updates the result limit setting.
     */
    fun setResultLimit(value: Int) {
        settings.putInt(KEY_RESULT_LIMIT, value)
    }

    /**
     * Updates the query timeout setting (in seconds).
     */
    fun setQueryTimeout(value: Int) {
        settings.putInt(KEY_QUERY_TIMEOUT, value)
    }

    /**
     * Updates the connection timeout setting (in seconds).
     */
    fun setConnectionTimeout(value: Int) {
        settings.putInt(KEY_CONNECTION_TIMEOUT, value)
    }

    /**
     * Updates the max connections setting.
     */
    fun setMaxConnections(value: Int) {
        settings.putInt(KEY_MAX_CONNECTIONS, value)
    }

    /**
     * Updates the dark mode setting.
     * Pass null to use system default.
     */
    fun setDarkMode(value: Boolean?) {
        if (value != null) {
            settings.putBoolean(KEY_DARK_MODE, value)
        } else {
            settings.remove(KEY_DARK_MODE)
        }
    }

    /**
     * Updates the sidebar collapsed state.
     */
    fun setSidebarCollapsed(value: Boolean) {
        settings.putBoolean(KEY_SIDEBAR_COLLAPSED, value)
    }

    /**
     * Saves all settings from AppSettings data class.
     */
    fun saveSettings(appSettings: AppSettings) {
        setResultLimit(appSettings.resultLimit)
        setQueryTimeout(appSettings.queryTimeoutSeconds)
        setConnectionTimeout(appSettings.connectionTimeoutSeconds)
        setMaxConnections(appSettings.maxConnections)
        setDarkMode(appSettings.darkMode)
        setSidebarCollapsed(appSettings.sidebarCollapsed)
    }
}
