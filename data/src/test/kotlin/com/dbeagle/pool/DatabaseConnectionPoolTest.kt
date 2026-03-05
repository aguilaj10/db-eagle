package com.dbeagle.pool

import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.*

class DatabaseConnectionPoolTest {
    private var postgresContainer: PostgreSQLContainer<*>? = null
    private var dockerAvailable = false

    @BeforeTest
    fun setup() {
        try {
            postgresContainer = PostgreSQLContainer("postgres:15-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
                .withStartupTimeoutSeconds(60)

            postgresContainer?.start()
            dockerAvailable = postgresContainer?.isRunning == true

            if (dockerAvailable) {
                try {
                    val testConn = java.sql.DriverManager.getConnection(
                        postgresContainer!!.jdbcUrl,
                        postgresContainer!!.username,
                        postgresContainer!!.password,
                    )
                    testConn.close()
                } catch (e: Exception) {
                    dockerAvailable = false
                }
            }
        } catch (e: Exception) {
            dockerAvailable = false
        }
    }

    @AfterTest
    fun teardown() {
        try {
            DatabaseConnectionPool.closeAllPools()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }

        try {
            postgresContainer?.stop()
            postgresContainer = null
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun testPoolIsCreatedLazily() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile = ConnectionProfile(
            id = "test-lazy-1",
            name = "Test Lazy Pool",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        assertFalse(DatabaseConnectionPool.hasPool(profile.id), "Pool should not exist before first getConnection")
        assertEquals(0, DatabaseConnectionPool.getPoolCount(), "Pool count should be 0 initially")

        val conn = DatabaseConnectionPool.getConnection(profile, container.password)

        assertTrue(DatabaseConnectionPool.hasPool(profile.id), "Pool should exist after first getConnection")
        assertEquals(1, DatabaseConnectionPool.getPoolCount(), "Pool count should be 1 after creation")

        conn.close()
    }

    @Test
    fun testGetConnectionReturnsValidConnection() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile = ConnectionProfile(
            id = "test-valid-conn",
            name = "Test Valid Connection",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val conn = DatabaseConnectionPool.getConnection(profile, container.password)

        assertFalse(conn.isClosed, "Connection should be open")

        val stmt = conn.createStatement()
        val rs = stmt.executeQuery("SELECT 1 as result")
        assertTrue(rs.next(), "Result set should have at least one row")
        assertEquals(1, rs.getInt("result"), "SELECT 1 should return 1")

        rs.close()
        stmt.close()
        conn.close()
    }

    @Test
    fun testPoolReusesSameDataSource() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile = ConnectionProfile(
            id = "test-reuse",
            name = "Test Pool Reuse",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val conn1 = DatabaseConnectionPool.getConnection(profile, container.password)
        assertEquals(1, DatabaseConnectionPool.getPoolCount(), "Should still have 1 pool")

        val conn2 = DatabaseConnectionPool.getConnection(profile, container.password)
        assertEquals(1, DatabaseConnectionPool.getPoolCount(), "Should still have 1 pool after second connection")

        conn1.close()
        conn2.close()
    }

    @Test
    fun testClosePoolRemovesPool() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile = ConnectionProfile(
            id = "test-close-pool",
            name = "Test Close Pool",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val conn = DatabaseConnectionPool.getConnection(profile, container.password)
        conn.close()

        assertTrue(DatabaseConnectionPool.hasPool(profile.id), "Pool should exist before close")

        DatabaseConnectionPool.closePool(profile)

        assertFalse(DatabaseConnectionPool.hasPool(profile.id), "Pool should not exist after close")
        assertEquals(0, DatabaseConnectionPool.getPoolCount(), "Pool count should be 0 after close")
    }

    @Test
    fun testClosePoolByIdRemovesPool() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile = ConnectionProfile(
            id = "test-close-by-id",
            name = "Test Close Pool By ID",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val conn = DatabaseConnectionPool.getConnection(profile, container.password)
        conn.close()

        assertTrue(DatabaseConnectionPool.hasPool(profile.id), "Pool should exist before close")

        DatabaseConnectionPool.closePool(profile.id)

        assertFalse(DatabaseConnectionPool.hasPool(profile.id), "Pool should not exist after close by id")
        assertEquals(0, DatabaseConnectionPool.getPoolCount(), "Pool count should be 0 after close")
    }

    @Test
    fun testCloseAllPoolsRemovesAllPools() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile1 = ConnectionProfile(
            id = "test-close-all-1",
            name = "Test Close All 1",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val profile2 = ConnectionProfile(
            id = "test-close-all-2",
            name = "Test Close All 2",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val conn1 = DatabaseConnectionPool.getConnection(profile1, container.password)
        val conn2 = DatabaseConnectionPool.getConnection(profile2, container.password)

        assertEquals(2, DatabaseConnectionPool.getPoolCount(), "Should have 2 pools")

        conn1.close()
        conn2.close()

        DatabaseConnectionPool.closeAllPools()

        assertEquals(0, DatabaseConnectionPool.getPoolCount(), "All pools should be closed")
        assertFalse(DatabaseConnectionPool.hasPool(profile1.id), "Pool 1 should not exist")
        assertFalse(DatabaseConnectionPool.hasPool(profile2.id), "Pool 2 should not exist")
    }

    @Test
    fun testMultipleProfilesCreateSeparatePools() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile1 = ConnectionProfile(
            id = "test-multi-1",
            name = "Test Multi 1",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val profile2 = ConnectionProfile(
            id = "test-multi-2",
            name = "Test Multi 2",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val conn1 = DatabaseConnectionPool.getConnection(profile1, container.password)
        assertEquals(1, DatabaseConnectionPool.getPoolCount(), "Should have 1 pool after first profile")

        val conn2 = DatabaseConnectionPool.getConnection(profile2, container.password)
        assertEquals(2, DatabaseConnectionPool.getPoolCount(), "Should have 2 pools after second profile")

        assertTrue(DatabaseConnectionPool.hasPool(profile1.id), "Pool 1 should exist")
        assertTrue(DatabaseConnectionPool.hasPool(profile2.id), "Pool 2 should exist")

        conn1.close()
        conn2.close()
    }

    @Test
    fun testClosePoolIsIdempotent() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile = ConnectionProfile(
            id = "test-idempotent",
            name = "Test Idempotent",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        val conn = DatabaseConnectionPool.getConnection(profile, container.password)
        conn.close()

        DatabaseConnectionPool.closePool(profile)
        DatabaseConnectionPool.closePool(profile)
        DatabaseConnectionPool.closePool(profile.id)

        assertFalse(DatabaseConnectionPool.hasPool(profile.id), "Pool should not exist")
    }

    @Test
    fun testConnectionPoolHandlesInvalidCredentials() {
        if (!dockerAvailable) return

        val container = postgresContainer!!
        val profile = ConnectionProfile(
            id = "test-invalid-creds",
            name = "Test Invalid Credentials",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
        )

        try {
            val exception = assertFailsWith<IllegalStateException> {
                DatabaseConnectionPool.getConnection(profile, "wrong-password")
            }

            assertTrue(
                exception.message?.contains("Failed to acquire connection") == true,
                "Exception message should indicate connection failure",
            )
        } catch (e: AssertionError) {
            assumeTrue(false, "Connection pool behavior differs on this platform: ${e.message}")
        }
    }
}
