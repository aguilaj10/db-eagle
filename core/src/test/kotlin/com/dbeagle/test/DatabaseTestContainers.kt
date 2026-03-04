package com.dbeagle.test

import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager

/**
 * PostgreSQL TestContainer helper providing database connection and lifecycle management.
 * Manages container startup/shutdown and provides JDBC connections for tests.
 */
object DatabaseTestContainers {
    private var container: PostgreSQLContainer<*>? = null
    private const val DATABASE_NAME = "testdb"
    private const val DATABASE_USER = "testuser"
    private const val DATABASE_PASSWORD = "testpass"

    /**
     * Starts PostgreSQL container if not already running.
     * Safe to call multiple times (idempotent).
     */
    fun startPostgres() {
        if (container == null) {
            container =
                PostgreSQLContainer("postgres:15-alpine")
                    .withDatabaseName(DATABASE_NAME)
                    .withUsername(DATABASE_USER)
                    .withPassword(DATABASE_PASSWORD)
                    .withStartupTimeoutSeconds(60)

            container!!.start()
        }
    }

    /**
     * Stops PostgreSQL container if running.
     * Safe to call multiple times (idempotent).
     */
    fun stopPostgres() {
        container?.stop()
        container = null
    }

    /**
     * Gets JDBC connection to the running PostgreSQL container.
     * Caller is responsible for closing the connection.
     *
     * @return JDBC Connection to the test database
     * @throws IllegalStateException if container not started
     */
    fun getConnection(): Connection {
        val c = container ?: throw IllegalStateException("PostgreSQL container not started. Call startPostgres() first.")
        return DriverManager.getConnection(c.jdbcUrl, c.username, c.password)
    }

    /**
     * Gets JDBC URL of the running PostgreSQL container.
     *
     * @return JDBC URL string
     * @throws IllegalStateException if container not started
     */
    fun getJdbcUrl(): String = container?.jdbcUrl ?: throw IllegalStateException("PostgreSQL container not started. Call startPostgres() first.")

    /**
     * Checks if PostgreSQL container is currently running.
     *
     * @return true if container is running, false otherwise
     */
    fun isRunning(): Boolean = container?.isRunning == true
}
