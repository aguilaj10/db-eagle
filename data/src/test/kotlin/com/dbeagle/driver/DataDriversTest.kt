package com.dbeagle.driver

import com.dbeagle.model.DatabaseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataDriversTest {
    @Test
    fun `registerAll registers PostgreSQL and SQLite drivers`() {
        DataDrivers.registerAll()

        val available = DatabaseDriverRegistry.listAvailableDrivers()
        assertTrue(available.size >= 2)
        assertTrue(available.contains(DatabaseType.PostgreSQL))
        assertTrue(available.contains(DatabaseType.SQLite))
    }

    @Test
    fun `registerAll provides PostgreSQL driver with correct name`() {
        DataDrivers.registerAll()

        val driver = DatabaseDriverRegistry.getDriver(DatabaseType.PostgreSQL)
        assertNotNull(driver)
        assertEquals("PostgreSQL", driver.getName())
    }

    @Test
    fun `registerAll provides SQLite driver with correct name`() {
        DataDrivers.registerAll()

        val driver = DatabaseDriverRegistry.getDriver(DatabaseType.SQLite)
        assertNotNull(driver)
        assertEquals("SQLite", driver.getName())
    }

    @Test
    fun `registerAll creates distinct driver instances`() {
        DataDrivers.registerAll()

        val pgDriver = DatabaseDriverRegistry.getDriver(DatabaseType.PostgreSQL)
        val sqliteDriver = DatabaseDriverRegistry.getDriver(DatabaseType.SQLite)

        assertNotNull(pgDriver)
        assertNotNull(sqliteDriver)
        assertTrue(pgDriver !== sqliteDriver)
    }
}
