package com.dbeagle.driver

import com.dbeagle.model.DatabaseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseDriverRegistryTest {
    
    @Test
    fun `registry starts empty before registration`() {
        DatabaseDriverRegistry.clear()
        
        val available = DatabaseDriverRegistry.listAvailableDrivers()
        assertTrue(available.isEmpty())
        assertNull(DatabaseDriverRegistry.getDriver(DatabaseType.PostgreSQL))
        assertNull(DatabaseDriverRegistry.getDriver(DatabaseType.SQLite))
    }
    
    @Test
    fun `registerDriver adds driver to registry`() {
        DatabaseDriverRegistry.clear()
        
        val mockDriver = MockDatabaseDriver()
        DatabaseDriverRegistry.registerDriver(DatabaseType.PostgreSQL, mockDriver)
        
        val retrieved = DatabaseDriverRegistry.getDriver(DatabaseType.PostgreSQL)
        assertNotNull(retrieved)
        assertEquals("Mock Driver", retrieved.getName())
    }
    
    @Test
    fun `registerDriver replaces existing driver for same type`() {
        DatabaseDriverRegistry.clear()
        
        val firstDriver = MockDatabaseDriver()
        val secondDriver = MockDatabaseDriver()
        
        DatabaseDriverRegistry.registerDriver(DatabaseType.PostgreSQL, firstDriver)
        DatabaseDriverRegistry.registerDriver(DatabaseType.PostgreSQL, secondDriver)
        
        val retrieved = DatabaseDriverRegistry.getDriver(DatabaseType.PostgreSQL)
        assertNotNull(retrieved)
        assertEquals("Mock Driver", retrieved.getName())
    }
    
    @Test
    fun `listAvailableDrivers returns all registered types`() {
        DatabaseDriverRegistry.clear()
        
        val pgDriver = MockDatabaseDriver()
        val sqliteDriver = MockDatabaseDriver()
        
        DatabaseDriverRegistry.registerDriver(DatabaseType.PostgreSQL, pgDriver)
        DatabaseDriverRegistry.registerDriver(DatabaseType.SQLite, sqliteDriver)
        
        val available = DatabaseDriverRegistry.listAvailableDrivers()
        assertEquals(2, available.size)
        assertTrue(available.contains(DatabaseType.PostgreSQL))
        assertTrue(available.contains(DatabaseType.SQLite))
    }
    
    @Test
    fun `getDriver returns null for unregistered type`() {
        DatabaseDriverRegistry.clear()
        
        val driver = DatabaseDriverRegistry.getDriver(DatabaseType.PostgreSQL)
        assertNull(driver)
    }
    
    @Test
    fun `multiple registrations work independently`() {
        DatabaseDriverRegistry.clear()
        
        val pgDriver = MockDatabaseDriver()
        val sqliteDriver = MockDatabaseDriver()
        
        DatabaseDriverRegistry.registerDriver(DatabaseType.PostgreSQL, pgDriver)
        DatabaseDriverRegistry.registerDriver(DatabaseType.SQLite, sqliteDriver)
        
        val retrievedPg = DatabaseDriverRegistry.getDriver(DatabaseType.PostgreSQL)
        val retrievedSqlite = DatabaseDriverRegistry.getDriver(DatabaseType.SQLite)
        
        assertNotNull(retrievedPg)
        assertNotNull(retrievedSqlite)
        assertEquals("Mock Driver", retrievedPg.getName())
        assertEquals("Mock Driver", retrievedSqlite.getName())
    }
}
