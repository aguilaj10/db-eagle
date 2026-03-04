package com.dbeagle.settings

import java.util.prefs.Preferences

object AppPreferences {
    private val preferences = Preferences.userRoot().node("com.dbeagle.settings")

    private const val KEY_RESULT_LIMIT = "resultLimit"
    private const val KEY_QUERY_TIMEOUT = "queryTimeoutSeconds"
    private const val KEY_CONNECTION_TIMEOUT = "connectionTimeoutSeconds"
    private const val KEY_MAX_CONNECTIONS = "maxConnections"

    fun load(): AppSettings = AppSettings(
        resultLimit = preferences.getInt(KEY_RESULT_LIMIT, AppSettings.DEFAULT_RESULT_LIMIT),
        queryTimeoutSeconds = preferences.getInt(KEY_QUERY_TIMEOUT, AppSettings.DEFAULT_QUERY_TIMEOUT_SECONDS),
        connectionTimeoutSeconds = preferences.getInt(KEY_CONNECTION_TIMEOUT, AppSettings.DEFAULT_CONNECTION_TIMEOUT_SECONDS),
        maxConnections = preferences.getInt(KEY_MAX_CONNECTIONS, AppSettings.DEFAULT_MAX_CONNECTIONS),
    )

    fun save(settings: AppSettings) {
        preferences.putInt(KEY_RESULT_LIMIT, settings.resultLimit)
        preferences.putInt(KEY_QUERY_TIMEOUT, settings.queryTimeoutSeconds)
        preferences.putInt(KEY_CONNECTION_TIMEOUT, settings.connectionTimeoutSeconds)
        preferences.putInt(KEY_MAX_CONNECTIONS, settings.maxConnections)
        preferences.flush()
    }
}
