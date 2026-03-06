package com.dbeagle.settings

import com.dbeagle.navigation.TabItem
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getBooleanOrNullFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        const val KEY_OPENED_TABS = "openedTabs"
        const val KEY_SELECTED_TAB_ID = "selectedTabId"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
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
     * Flow of opened tabs (empty list if no tabs saved).
     */
    val openedTabsFlow: Flow<List<TabItem>> = settings
        .getStringOrNullFlow(KEY_OPENED_TABS)
        .map { jsonString ->
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<TabItem>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
        .distinctUntilChanged()

    /**
     * Flow of selected tab ID (null if no tab selected).
     */
    val selectedTabIdFlow: Flow<String?> = settings
        .getStringOrNullFlow(KEY_SELECTED_TAB_ID)
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
        openedTabsFlow,
        selectedTabIdFlow,
    ) { timeouts: Array<out Any>, darkMode, sidebarCollapsed, openedTabs, selectedTabId ->
        @Suppress("UNCHECKED_CAST")
        val arr = timeouts as Array<out Int>
        AppSettings(
            resultLimit = arr[0],
            queryTimeoutSeconds = arr[1],
            connectionTimeoutSeconds = arr[2],
            maxConnections = arr[3],
            darkMode = darkMode,
            sidebarCollapsed = sidebarCollapsed,
            openedTabs = openedTabs,
            selectedTabId = selectedTabId,
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
     * Saves opened tabs and selected tab ID.
     * Serializes tabs to JSON for persistence.
     */
    fun saveOpenedTabs(
        tabs: List<TabItem>,
        selectedId: String?,
    ) {
        val jsonString = json.encodeToString(tabs)
        settings.putString(KEY_OPENED_TABS, jsonString)
        if (selectedId != null) {
            settings.putString(KEY_SELECTED_TAB_ID, selectedId)
        } else {
            settings.remove(KEY_SELECTED_TAB_ID)
        }
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
        saveOpenedTabs(appSettings.openedTabs, appSettings.selectedTabId)
    }
}
