package com.dbeagle.settings

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesRepositoryTest {
    private lateinit var mockSettings: MapSettings
    private lateinit var repository: AppPreferencesRepository

    @BeforeTest
    fun setup() {
        mockSettings = MapSettings()
        repository = AppPreferencesRepository(mockSettings)
    }

    @Test
    fun `settingsFlow emits defaults when no settings saved`() = runTest {
        val settings = repository.settingsFlow.first()
        assertEquals(1000, settings.resultLimit)
        assertEquals(60, settings.queryTimeoutSeconds)
        assertEquals(30, settings.connectionTimeoutSeconds)
        assertEquals(10, settings.maxConnections)
        assertEquals(null, settings.darkMode)
    }

    @Test
    fun `settingsFlow emits updated value when result limit changes`() = runTest {
        repository.setResultLimit(500)
        val settings = repository.settingsFlow.first()
        assertEquals(500, settings.resultLimit)
    }

    @Test
    fun `settingsFlow emits updated value when query timeout changes`() = runTest {
        repository.setQueryTimeout(120)
        val settings = repository.settingsFlow.first()
        assertEquals(120, settings.queryTimeoutSeconds)
    }

    @Test
    fun `settingsFlow emits updated value when connection timeout changes`() = runTest {
        repository.setConnectionTimeout(45)
        val settings = repository.settingsFlow.first()
        assertEquals(45, settings.connectionTimeoutSeconds)
    }

    @Test
    fun `settingsFlow emits updated value when max connections changes`() = runTest {
        repository.setMaxConnections(20)
        val settings = repository.settingsFlow.first()
        assertEquals(20, settings.maxConnections)
    }

    @Test
    fun `settingsFlow emits updated value when dark mode changes`() = runTest {
        repository.setDarkMode(true)
        val settings = repository.settingsFlow.first()
        assertEquals(true, settings.darkMode)
    }

    @Test
    fun `settingsFlow emits null when dark mode is cleared`() = runTest {
        repository.setDarkMode(true)
        repository.setDarkMode(null)
        val settings = repository.settingsFlow.first()
        assertEquals(null, settings.darkMode)
    }

    @Test
    fun `saveSettings persists all settings and emits through flow`() = runTest {
        val custom = AppSettings(
            resultLimit = 500,
            queryTimeoutSeconds = 120,
            connectionTimeoutSeconds = 45,
            maxConnections = 20,
            darkMode = true,
        )
        repository.saveSettings(custom)
        val loaded = repository.settingsFlow.first()
        assertEquals(custom, loaded)
    }

    @Test
    fun `resultLimitFlow emits default value initially`() = runTest {
        val value = repository.resultLimitFlow.first()
        assertEquals(1000, value)
    }

    @Test
    fun `resultLimitFlow emits updated value after change`() = runTest {
        repository.setResultLimit(500)
        val value = repository.resultLimitFlow.first()
        assertEquals(500, value)
    }

    @Test
    fun `queryTimeoutFlow emits default value initially`() = runTest {
        val value = repository.queryTimeoutFlow.first()
        assertEquals(60, value)
    }

    @Test
    fun `queryTimeoutFlow emits updated value after change`() = runTest {
        repository.setQueryTimeout(120)
        val value = repository.queryTimeoutFlow.first()
        assertEquals(120, value)
    }

    @Test
    fun `connectionTimeoutFlow emits default value initially`() = runTest {
        val value = repository.connectionTimeoutFlow.first()
        assertEquals(30, value)
    }

    @Test
    fun `connectionTimeoutFlow emits updated value after change`() = runTest {
        repository.setConnectionTimeout(45)
        val value = repository.connectionTimeoutFlow.first()
        assertEquals(45, value)
    }

    @Test
    fun `maxConnectionsFlow emits default value initially`() = runTest {
        val value = repository.maxConnectionsFlow.first()
        assertEquals(10, value)
    }

    @Test
    fun `maxConnectionsFlow emits updated value after change`() = runTest {
        repository.setMaxConnections(20)
        val value = repository.maxConnectionsFlow.first()
        assertEquals(20, value)
    }

    @Test
    fun `darkModeFlow emits null initially`() = runTest {
        val value = repository.darkModeFlow.first()
        assertEquals(null, value)
    }

    @Test
    fun `darkModeFlow emits true after setting to true`() = runTest {
        repository.setDarkMode(true)
        val value = repository.darkModeFlow.first()
        assertEquals(true, value)
    }

    @Test
    fun `darkModeFlow emits false after setting to false`() = runTest {
        repository.setDarkMode(false)
        val value = repository.darkModeFlow.first()
        assertEquals(false, value)
    }

    @Test
    fun `darkModeFlow emits null after clearing`() = runTest {
        repository.setDarkMode(true)
        repository.setDarkMode(null)
        val value = repository.darkModeFlow.first()
        assertEquals(null, value)
    }
}
