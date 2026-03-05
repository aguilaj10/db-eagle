package com.dbeagle.settings

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanOrNullFlow
import com.russhwolf.settings.coroutines.getIntFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Repository providing reactive Flow-based access to application settings.
 * Wraps multiplatform-settings ObservableSettings with Flow API for reactive updates.
 */
class AppPreferencesRepository(
    private val settings: ObservableSettings
) {
    private companion object {
        const val KEY_RESULT_LIMIT = "resultLimit"
        const val KEY_QUERY_TIMEOUT = "queryTimeoutSeconds"
        const val KEY_CONNECTION_TIMEOUT = "connectionTimeoutSeconds"
        const val KEY_MAX_CONNECTIONS = "maxConnections"
        const val KEY_DARK_MODE = "darkMode"
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
     * Combined flow of all settings as AppSettings data class.
     * Emits new value whenever any setting changes.
     */
    val settingsFlow: Flow<AppSettings> = combine(
        resultLimitFlow,
        queryTimeoutFlow,
        connectionTimeoutFlow,
        maxConnectionsFlow,
        darkModeFlow
    ) { resultLimit, queryTimeout, connectionTimeout, maxConnections, darkMode ->
        AppSettings(
            resultLimit = resultLimit,
            queryTimeoutSeconds = queryTimeout,
            connectionTimeoutSeconds = connectionTimeout,
            maxConnections = maxConnections,
            darkMode = darkMode
        )
    }

    /**
     * Updates the result limit setting.
     */
    suspend fun setResultLimit(value: Int) {
        settings.putInt(KEY_RESULT_LIMIT, value)
    }

    /**
     * Updates the query timeout setting (in seconds).
     */
    suspend fun setQueryTimeout(value: Int) {
        settings.putInt(KEY_QUERY_TIMEOUT, value)
    }

    /**
     * Updates the connection timeout setting (in seconds).
     */
    suspend fun setConnectionTimeout(value: Int) {
        settings.putInt(KEY_CONNECTION_TIMEOUT, value)
    }

    /**
     * Updates the max connections setting.
     */
    suspend fun setMaxConnections(value: Int) {
        settings.putInt(KEY_MAX_CONNECTIONS, value)
    }

    /**
     * Updates the dark mode setting.
     * Pass null to use system default.
     */
    suspend fun setDarkMode(value: Boolean?) {
        if (value != null) {
            settings.putBoolean(KEY_DARK_MODE, value)
        } else {
            settings.remove(KEY_DARK_MODE)
        }
    }

    /**
     * Saves all settings from AppSettings data class.
     */
    suspend fun saveSettings(appSettings: AppSettings) {
        setResultLimit(appSettings.resultLimit)
        setQueryTimeout(appSettings.queryTimeoutSeconds)
        setConnectionTimeout(appSettings.connectionTimeoutSeconds)
        setMaxConnections(appSettings.maxConnections)
        setDarkMode(appSettings.darkMode)
    }
}
