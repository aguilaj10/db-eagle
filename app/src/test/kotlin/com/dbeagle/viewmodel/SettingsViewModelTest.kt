package com.dbeagle.viewmodel

import com.dbeagle.settings.AppPreferencesRepository
import com.dbeagle.settings.AppSettings
import com.dbeagle.theme.ThemeManager
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SettingsViewModelTest {

    private lateinit var settings: MapSettings
    private lateinit var appPreferencesRepository: AppPreferencesRepository
    private lateinit var themeManager: ThemeManager

    @BeforeTest
    fun setup() {
        settings = MapSettings()
        appPreferencesRepository = AppPreferencesRepository(settings)
        themeManager = ThemeManager(appPreferencesRepository)
    }

    @AfterTest
    fun cleanup() {
        settings.clear()
    }

    @Test
    fun initialState_loadsFromPreferences() = runBlocking {
        val customSettings = AppSettings(
            resultLimit = 500,
            queryTimeoutSeconds = 90,
            connectionTimeoutSeconds = 45,
            maxConnections = 15,
        )
        appPreferencesRepository.saveSettings(customSettings)

        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)
        // Wait for init block Flow collection to complete
        delay(200)
        val state = viewModel.uiState.value

        assertEquals(500, state.settings.resultLimit, "Should load custom resultLimit")
        assertEquals("500", state.resultLimitInput, "Should initialize resultLimitInput from settings")
        assertEquals(90, state.settings.queryTimeoutSeconds, "Should load custom queryTimeoutSeconds")
        assertEquals("90", state.queryTimeoutInput, "Should initialize queryTimeoutInput from settings")
        assertEquals(45, state.settings.connectionTimeoutSeconds, "Should load custom connectionTimeoutSeconds")
        assertEquals("45", state.connectionTimeoutInput, "Should initialize connectionTimeoutInput from settings")
        assertEquals(15, state.settings.maxConnections, "Should load custom maxConnections")
        assertEquals("15", state.maxConnectionsInput, "Should initialize maxConnectionsInput from settings")
        assertNull(state.errorMessage, "Initial errorMessage should be null")
    }

    @Test
    fun updateResultLimit_updatesInputState() {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateResultLimit("2000")

        val state = viewModel.uiState.value
        assertEquals("2000", state.resultLimitInput, "resultLimitInput should be updated")
    }

    @Test
    fun updateQueryTimeout_updatesInputState() {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateQueryTimeout("120")

        val state = viewModel.uiState.value
        assertEquals("120", state.queryTimeoutInput, "queryTimeoutInput should be updated")
    }

    @Test
    fun updateConnectionTimeout_updatesInputState() {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateConnectionTimeout("60")

        val state = viewModel.uiState.value
        assertEquals("60", state.connectionTimeoutInput, "connectionTimeoutInput should be updated")
    }

    @Test
    fun updateMaxConnections_updatesInputState() {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateMaxConnections("20")

        val state = viewModel.uiState.value
        assertEquals("20", state.maxConnectionsInput, "maxConnectionsInput should be updated")
    }

    @Test
    fun saveSettings_withValidInput_savesSuccessfully() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateResultLimit("750")
        viewModel.updateQueryTimeout("75")
        viewModel.updateConnectionTimeout("40")
        viewModel.updateMaxConnections("12")

        viewModel.saveSettings()

        delay(200)

        val state = viewModel.uiState.value
        assertEquals(750, state.settings.resultLimit, "Settings should be saved with new resultLimit")
        assertEquals(75, state.settings.queryTimeoutSeconds, "Settings should be saved with new queryTimeoutSeconds")
        assertEquals(40, state.settings.connectionTimeoutSeconds, "Settings should be saved with new connectionTimeoutSeconds")
        assertEquals(12, state.settings.maxConnections, "Settings should be saved with new maxConnections")
        assertNull(state.errorMessage, "No error should be present after successful save")

        val loadedSettings = appPreferencesRepository.settingsFlow.first()
        assertEquals(750, loadedSettings.resultLimit, "Settings should be persisted to preferences")
        assertEquals(75, loadedSettings.queryTimeoutSeconds, "Settings should be persisted to preferences")
        assertEquals(40, loadedSettings.connectionTimeoutSeconds, "Settings should be persisted to preferences")
        assertEquals(12, loadedSettings.maxConnections, "Settings should be persisted to preferences")
    }

    @Test
    fun saveSettings_withNonNumericInput_setsErrorMessage() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateResultLimit("not-a-number")
        viewModel.saveSettings()

        delay(200)

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage, "Error message should be set")
        assertEquals(
            "Invalid number format. Please enter valid integers.",
            state.errorMessage,
            "Should set number format error message",
        )
    }

    @Test
    fun saveSettings_withEmptyInput_setsErrorMessage() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateQueryTimeout("")
        viewModel.saveSettings()

        delay(200)

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage, "Error message should be set for empty input")
    }

    @Test
    fun saveSettings_withNegativeValue_setsValidationError() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        // Wait for init block to load settings into input fields
        viewModel.uiState.first { it.resultLimitInput.isNotEmpty() }

        viewModel.updateResultLimit("-100")
        viewModel.saveSettings()

        delay(200)

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage, "Error message should be set for negative value")
    }

    @Test
    fun saveSettings_withZeroValue_setsValidationError() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        // Wait for init block to load settings into input fields
        viewModel.uiState.first { it.maxConnectionsInput.isNotEmpty() }

        viewModel.updateMaxConnections("0")
        viewModel.saveSettings()

        delay(200)

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage, "Error message should be set for zero value")
        assertEquals(
            "maxConnections must be > 0",
            state.errorMessage,
            "Should set validation error message",
        )
    }

    @Test
    fun resetToDefaults_resetsAllFieldsToDefaults() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateResultLimit("9999")
        viewModel.updateQueryTimeout("999")
        viewModel.updateConnectionTimeout("999")
        viewModel.updateMaxConnections("999")
        viewModel.saveSettings()
        delay(200)

        viewModel.resetToDefaults()
        delay(200)

        val state = viewModel.uiState.value
        assertEquals(AppSettings.DEFAULT_RESULT_LIMIT, state.settings.resultLimit, "Should reset to default resultLimit")
        assertEquals(
            AppSettings.DEFAULT_RESULT_LIMIT.toString(),
            state.resultLimitInput,
            "Input field should reset to default resultLimit",
        )
        assertEquals(
            AppSettings.DEFAULT_QUERY_TIMEOUT_SECONDS,
            state.settings.queryTimeoutSeconds,
            "Should reset to default queryTimeoutSeconds",
        )
        assertEquals(
            AppSettings.DEFAULT_QUERY_TIMEOUT_SECONDS.toString(),
            state.queryTimeoutInput,
            "Input field should reset to default queryTimeoutSeconds",
        )
        assertEquals(
            AppSettings.DEFAULT_CONNECTION_TIMEOUT_SECONDS,
            state.settings.connectionTimeoutSeconds,
            "Should reset to default connectionTimeoutSeconds",
        )
        assertEquals(
            AppSettings.DEFAULT_CONNECTION_TIMEOUT_SECONDS.toString(),
            state.connectionTimeoutInput,
            "Input field should reset to default connectionTimeoutSeconds",
        )
        assertEquals(
            AppSettings.DEFAULT_MAX_CONNECTIONS,
            state.settings.maxConnections,
            "Should reset to default maxConnections",
        )
        assertEquals(
            AppSettings.DEFAULT_MAX_CONNECTIONS.toString(),
            state.maxConnectionsInput,
            "Input field should reset to default maxConnections",
        )
        assertNull(state.errorMessage, "Error message should be cleared after reset")

        val loadedSettings = appPreferencesRepository.settingsFlow.first()
        assertEquals(
            AppSettings.DEFAULT_RESULT_LIMIT,
            loadedSettings.resultLimit,
            "Defaults should be persisted to preferences",
        )
        assertEquals(
            AppSettings.DEFAULT_QUERY_TIMEOUT_SECONDS,
            loadedSettings.queryTimeoutSeconds,
            "Defaults should be persisted to preferences",
        )
        assertEquals(
            AppSettings.DEFAULT_CONNECTION_TIMEOUT_SECONDS,
            loadedSettings.connectionTimeoutSeconds,
            "Defaults should be persisted to preferences",
        )
        assertEquals(
            AppSettings.DEFAULT_MAX_CONNECTIONS,
            loadedSettings.maxConnections,
            "Defaults should be persisted to preferences",
        )
    }

    @Test
    fun resetToDefaults_clearsPreviousErrorMessage() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.updateResultLimit("invalid")
        viewModel.saveSettings()
        delay(200)

        assertNotNull(viewModel.uiState.value.errorMessage, "Error should be set")

        viewModel.resetToDefaults()
        delay(200)

        assertNull(viewModel.uiState.value.errorMessage, "Error should be cleared after reset")
    }

    @Test
    fun refreshPoolStats_updatesPoolCountAndStats() = runBlocking {
        val viewModel = SettingsViewModel(appPreferencesRepository, themeManager)

        viewModel.refreshPoolStats()
        delay(200)

        val state = viewModel.uiState.value
        assert(state.poolCount >= 0) { "Pool count should be non-negative" }
        assertNotNull(state.allPools, "allPools should not be null")
    }
}
