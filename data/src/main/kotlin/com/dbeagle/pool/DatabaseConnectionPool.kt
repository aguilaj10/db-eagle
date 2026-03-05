package com.dbeagle.pool

import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

/**
 * HikariCP-based connection pool manager for database connections.
 * Provides lazy initialization of connection pools per ConnectionProfile.id.
 *
 * Thread-safe singleton managing multiple connection pools.
 *
 * Default Pool Configuration:
 * - Maximum pool size: 10
 * - Connection timeout: 30 seconds
 * - Idle timeout: 10 minutes
 * - Max lifetime: 30 minutes
 * - Leak detection threshold: 30 seconds
 *
 * Pool exhaustion throws SQLException with retry suggestion.
 */
object DatabaseConnectionPool {
    private val pools = ConcurrentHashMap<String, HikariDataSource>()

    // Default pool configuration constants
    private const val DEFAULT_MAX_POOL_SIZE = 10
    private const val DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L // 30 seconds
    private const val DEFAULT_IDLE_TIMEOUT_MS = 600_000L // 10 minutes
    private const val DEFAULT_MAX_LIFETIME_MS = 1_800_000L // 30 minutes
    private const val DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 30_000L // 30 seconds

    data class PoolStats(
        val active: Int,
        val idle: Int,
        val total: Int,
        val waiting: Int,
    )

    /**
     * Get a connection from the pool for the given profile.
     * Initializes the pool lazily on first access.
     *
     * @param profile Connection profile containing database credentials and configuration
     * @param decryptedPassword Plaintext password (decrypted by caller)
     * @return JDBC Connection from the pool
     * @throws SQLException if pool exhausted or connection fails
     */
    fun getConnection(
        profile: ConnectionProfile,
        decryptedPassword: String,
    ): Connection {
        val dataSource =
            pools.computeIfAbsent(profile.id) {
                createDataSource(profile, decryptedPassword)
            }

        return try {
            dataSource.connection
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to acquire connection from pool for profile '${profile.name}'. " +
                    "Pool may be exhausted (consider retrying after active connections are released). " +
                    "Current pool stats: active=${dataSource.hikariPoolMXBean?.activeConnections ?: 0}, " +
                    "idle=${dataSource.hikariPoolMXBean?.idleConnections ?: 0}, " +
                    "total=${dataSource.hikariPoolMXBean?.totalConnections ?: 0}, " +
                    "waiting=${dataSource.hikariPoolMXBean?.threadsAwaitingConnection ?: 0}",
                e,
            )
        }
    }

    /**
     * Close the connection pool for the given profile.
     * Removes pool from internal map after closing.
     * Idempotent - safe to call multiple times.
     *
     * @param profile Connection profile whose pool should be closed
     */
    fun closePool(profile: ConnectionProfile) {
        closePool(profile.id)
    }

    /**
     * Close the connection pool for the given profile ID.
     * Removes pool from internal map after closing.
     * Idempotent - safe to call multiple times.
     *
     * @param profileId Connection profile ID whose pool should be closed
     */
    fun closePool(profileId: String) {
        pools.remove(profileId)?.close()
    }

    /**
     * Close all active connection pools and clear internal map.
     * Idempotent - safe to call multiple times.
     * Use for application shutdown or full reset.
     */
    fun closeAllPools() {
        pools.values.forEach { it.close() }
        pools.clear()
    }

    /**
     * Get the current number of active connection pools.
     * @return Number of initialized pools
     */
    fun getPoolCount(): Int = pools.size

    fun getPoolStats(profileId: String): PoolStats? {
        val dataSource = pools[profileId] ?: return null
        val mx = dataSource.hikariPoolMXBean ?: return null
        return PoolStats(
            active = mx.activeConnections,
            idle = mx.idleConnections,
            total = mx.totalConnections,
            waiting = mx.threadsAwaitingConnection,
        )
    }

    fun getAllPoolStats(): Map<String, PoolStats> = pools.keys.associateWith { id ->
        getPoolStats(id) ?: PoolStats(active = 0, idle = 0, total = 0, waiting = 0)
    }

    /**
     * Check if a pool exists for the given profile ID.
     * @param profileId Connection profile ID
     * @return true if pool exists, false otherwise
     */
    fun hasPool(profileId: String): Boolean = pools.containsKey(profileId)

    /**
     * Create HikariCP DataSource for the given connection profile.
     * Configures JDBC URL, credentials, and pool settings.
     *
     * @param profile Connection profile containing database configuration
     * @param password Plaintext password
     * @return Configured HikariDataSource
     */
    private fun createDataSource(
        profile: ConnectionProfile,
        password: String,
    ): HikariDataSource {
        val config =
            HikariConfig().apply {
                jdbcUrl = buildJdbcUrl(profile)
                username = profile.username
                this.password = password

                maximumPoolSize = DEFAULT_MAX_POOL_SIZE
                connectionTimeout = DEFAULT_CONNECTION_TIMEOUT_MS
                idleTimeout = DEFAULT_IDLE_TIMEOUT_MS
                maxLifetime = DEFAULT_MAX_LIFETIME_MS
                leakDetectionThreshold = DEFAULT_LEAK_DETECTION_THRESHOLD_MS

                // Pool naming for monitoring
                poolName = "DBEagle-${profile.name}-${profile.id.take(8)}"

                // Additional HikariCP best practices
                isAutoCommit = true
                transactionIsolation = "TRANSACTION_READ_COMMITTED"

                // Apply custom options from profile if any
                profile.options.forEach { (key, value) ->
                    addDataSourceProperty(key, value)
                }
            }

        return HikariDataSource(config)
    }

    /**
     * Build JDBC URL from connection profile.
     * Supports PostgreSQL and SQLite.
     *
     * @param profile Connection profile
     * @return JDBC URL string
     */
    private fun buildJdbcUrl(profile: ConnectionProfile): String = when (profile.type) {
        is DatabaseType.PostgreSQL -> {
            "jdbc:postgresql://${profile.host}:${profile.port}/${profile.database}"
        }
        is DatabaseType.SQLite -> {
            // SQLite uses file path in database field
            "jdbc:sqlite:${profile.database}"
        }
    }
}
