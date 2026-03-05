package com.dbeagle.viewmodel

import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.settings.AppPreferencesRepository
import com.dbeagle.settings.AppSettings
import com.dbeagle.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * ViewModel for managing application settings and pool statistics.
 * Handles settings persistence, validation, and debug pool info display.
 */
class SettingsViewModel(
    private val repository: AppPreferencesRepository,
    private val themeManager: ThemeManager,
) : BaseViewModel() {

    /**
     * UI state for the settings screen.
     */
    data class SettingsUiState(
        val settings: AppSettings,
        val resultLimitInput: String = "",
        val queryTimeoutInput: String = "",
        val connectionTimeoutInput: String = "",
        val maxConnectionsInput: String = "",
        val errorMessage: String? = null,
        val poolCount: Int = 0,
        val allPools: Map<String, DatabaseConnectionPool.PoolStats> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(SettingsUiState(settings = AppSettings()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val darkMode: StateFlow<Boolean?> = themeManager.darkModeOverride

    fun setDarkMode(enabled: Boolean) {
        themeManager.setDarkMode(enabled)
    }

    init {
        viewModelScope.launch {
            repository.settingsFlow
                .distinctUntilChanged()
                .collect { settings ->
                    updateStateFlow(_uiState) { currentState ->
                        currentState.copy(
                            settings = settings,
                            resultLimitInput = settings.resultLimit.toString(),
                            queryTimeoutInput = settings.queryTimeoutSeconds.toString(),
                            connectionTimeoutInput = settings.connectionTimeoutSeconds.toString(),
                            maxConnectionsInput = settings.maxConnections.toString(),
                        )
                    }
                }
        }
        refreshPoolStats()
    }

    /**
     * Update result limit input field.
     */
    fun updateResultLimit(value: String) {
        updateStateFlow(_uiState) { it.copy(resultLimitInput = value) }
    }

    /**
     * Update query timeout input field.
     */
    fun updateQueryTimeout(value: String) {
        updateStateFlow(_uiState) { it.copy(queryTimeoutInput = value) }
    }

    /**
     * Update connection timeout input field.
     */
    fun updateConnectionTimeout(value: String) {
        updateStateFlow(_uiState) { it.copy(connectionTimeoutInput = value) }
    }

    /**
     * Update max connections input field.
     */
    fun updateMaxConnections(value: String) {
        updateStateFlow(_uiState) { it.copy(maxConnectionsInput = value) }
    }

    /**
     * Save current input values to persistent settings.
     * Validates all inputs before saving.
     */
    fun saveSettings() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val newSettings = AppSettings(
                    resultLimit = currentState.resultLimitInput.toInt(),
                    queryTimeoutSeconds = currentState.queryTimeoutInput.toInt(),
                    connectionTimeoutSeconds = currentState.connectionTimeoutInput.toInt(),
                    maxConnections = currentState.maxConnectionsInput.toInt(),
                )
                repository.saveSettings(newSettings)
                updateStateFlow(_uiState) {
                    it.copy(
                        settings = newSettings,
                        errorMessage = null,
                    )
                }
            } catch (e: NumberFormatException) {
                updateStateFlow(_uiState) {
                    it.copy(errorMessage = "Invalid number format. Please enter valid integers.")
                }
            } catch (e: IllegalArgumentException) {
                updateStateFlow(_uiState) {
                    it.copy(
                        errorMessage = e.message
                            ?: "Invalid settings values. All values must be greater than 0.",
                    )
                }
            }
        }
    }

    /**
     * Reset all settings to their default values.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            val defaultSettings = AppSettings()
            repository.saveSettings(defaultSettings)
            updateStateFlow(_uiState) {
                it.copy(
                    settings = defaultSettings,
                    resultLimitInput = defaultSettings.resultLimit.toString(),
                    queryTimeoutInput = defaultSettings.queryTimeoutSeconds.toString(),
                    connectionTimeoutInput = defaultSettings.connectionTimeoutSeconds.toString(),
                    maxConnectionsInput = defaultSettings.maxConnections.toString(),
                    errorMessage = null,
                )
            }
        }
    }

    /**
     * Refresh connection pool statistics from DatabaseConnectionPool.
     */
    fun refreshPoolStats() {
        viewModelScope.launch {
            val poolCount = DatabaseConnectionPool.getPoolCount()
            val allPools = DatabaseConnectionPool.getAllPoolStats()
            updateStateFlow(_uiState) {
                it.copy(
                    poolCount = poolCount,
                    allPools = allPools,
                )
            }
        }
    }
}
