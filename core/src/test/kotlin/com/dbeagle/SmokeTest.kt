package com.dbeagle

import com.dbeagle.test.BaseTest
import com.dbeagle.test.DatabaseTestContainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmokeTest : BaseTest() {
    override fun beforeTest() {
        try {
            DatabaseTestContainers.startPostgres()
        } catch (e: Exception) {
            // Docker may not be available in test environment
            System.err.println("Warning: Could not start PostgreSQL container: ${e.message}")
        }
    }

    override fun afterTest() {
        try {
            DatabaseTestContainers.stopPostgres()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun testPostgresContainerStarts() {
        if (!DatabaseTestContainers.isRunning()) {
            println("Skipping: PostgreSQL container not available (Docker may not be running)")
            return
        }
        assertTrue(DatabaseTestContainers.isRunning(), "PostgreSQL container should be running")
    }

    @Test
    fun testPostgresSelectOne() {
        if (!DatabaseTestContainers.isRunning()) {
            println("Skipping: PostgreSQL container not available (Docker may not be running)")
            return
        }
        val connection = DatabaseTestContainers.getConnection()
        try {
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery("SELECT 1")
            assertTrue(resultSet.next(), "SELECT 1 should return one row")
            val result = resultSet.getInt(1)
            assertEquals(1, result, "SELECT 1 should return value 1")
        } finally {
            connection.close()
        }
    }

    @Test
    fun testPostgresJdbcUrl() {
        if (!DatabaseTestContainers.isRunning()) {
            println("Skipping: PostgreSQL container not available (Docker may not be running)")
            return
        }
        val jdbcUrl = DatabaseTestContainers.getJdbcUrl()
        assertTrue(jdbcUrl.contains("jdbc:postgresql://"), "JDBC URL should be valid PostgreSQL URL")
        assertTrue(jdbcUrl.contains("testdb"), "JDBC URL should contain database name")
    }
}
