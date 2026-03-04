package com.dbeagle.driver

import com.dbeagle.model.DatabaseType

/**
 * Singleton registry for managing database driver instances.
 * 
 * This registry provides a central location for registering and retrieving
 * database drivers based on their DatabaseType. It supports:
 * - Manual registration of drivers (for plugins)
 * - Retrieval of drivers by type
 * - Listing all available driver types
 * 
 * Thread-safe for concurrent access.
 */
object DatabaseDriverRegistry {
    private val drivers = mutableMapOf<DatabaseType, DatabaseDriver>()
    
    /**
     * Registers a driver for the specified database type.
     * 
     * If a driver is already registered for this type, it will be replaced.
     * 
     * @param type The database type this driver handles
     * @param driver The driver instance to register
     */
    @Synchronized
    fun registerDriver(type: DatabaseType, driver: DatabaseDriver) {
        drivers[type] = driver
    }
    
    /**
     * Retrieves the driver for the specified database type.
     * 
     * @param type The database type to get a driver for
     * @return The registered driver, or null if no driver is registered for this type
     */
    @Synchronized
    fun getDriver(type: DatabaseType): DatabaseDriver? {
        return drivers[type]
    }
    
    /**
     * Lists all database types that have registered drivers.
     * 
     * @return A list of DatabaseType instances with registered drivers
     */
    @Synchronized
    fun listAvailableDrivers(): List<DatabaseType> {
        return drivers.keys.toList()
    }
    
    /**
     * Clears all registered drivers. Primarily used for testing.
     */
    @Synchronized
    internal fun clear() {
        drivers.clear()
    }
}
