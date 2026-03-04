package com.dbeagle.driver

/**
 * Manages a pool of database connections for efficient resource reuse.
 * Implementations handle connection creation, validation, and recycling.
 */
interface ConnectionPool {
    /**
     * Acquires a connection from the pool. May block if no connections are available
     * and the pool has reached maximum capacity.
     */
    suspend fun acquire(): DatabaseDriver

    /**
     * Returns a connection to the pool for reuse.
     */
    suspend fun release(driver: DatabaseDriver)

    /**
     * Closes all connections in the pool and prevents new acquisitions.
     */
    suspend fun shutdown()

    /**
     * Returns the current number of connections held by the pool.
     */
    fun getPoolSize(): Int

    /**
     * Returns the number of available (unused) connections in the pool.
     */
    fun getAvailableCount(): Int
}
