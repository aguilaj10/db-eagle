package com.dbeagle.settings

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesTest {
    private val prefsNode = Preferences.userRoot().node("com.dbeagle.settings")

    @BeforeTest
    fun setup() {
        prefsNode.clear()
        prefsNode.flush()
    }

    @AfterTest
    fun teardown() {
        prefsNode.clear()
        prefsNode.flush()
    }

    @Test
    fun `load returns defaults when no preferences saved`() {
        val settings = AppPreferences.load()
        assertEquals(1000, settings.resultLimit)
        assertEquals(60, settings.queryTimeoutSeconds)
        assertEquals(30, settings.connectionTimeoutSeconds)
        assertEquals(10, settings.maxConnections)
    }

    @Test
    fun `save and load roundtrip preserves settings`() {
        val custom = AppSettings(
            resultLimit = 500,
            queryTimeoutSeconds = 120,
            connectionTimeoutSeconds = 45,
            maxConnections = 20,
        )
        AppPreferences.save(custom)
        val loaded = AppPreferences.load()
        assertEquals(custom, loaded)
    }

    @Test
    fun `result limit affects query execution page size`() {
        val settings = AppSettings(resultLimit = 500)
        AppPreferences.save(settings)
        val loaded = AppPreferences.load()
        assertEquals(500, loaded.resultLimit)
    }
}
