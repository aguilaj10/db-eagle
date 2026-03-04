package com.dbeagle.driver

import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.model.QueryResult
import com.dbeagle.pool.DatabaseConnectionPool
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgreSQLDriverTest {
    private var postgresContainer: PostgreSQLContainer<*>? = null
    private var dockerAvailable = false

    @BeforeTest
    fun setup() {
        try {
            postgresContainer =
                PostgreSQLContainer("postgres:15-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withStartupTimeoutSeconds(60)

            postgresContainer?.start()
            dockerAvailable = postgresContainer?.isRunning == true

            if (dockerAvailable) {
                seedSchema(postgresContainer!!)
            }
        } catch (_: Exception) {
            // Docker not available - tests will be skipped
            dockerAvailable = false
        }
    }

    @AfterTest
    fun teardown() {
        try {
            DatabaseConnectionPool.closeAllPools()
        } catch (_: Exception) {
            // Ignore cleanup errors
        }

        try {
            postgresContainer?.stop()
            postgresContainer = null
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun testExecuteQuerySelect1() = kotlinx.coroutines.runBlocking {
        if (!dockerAvailable) return@runBlocking

        val container = postgresContainer!!
        val driver = PostgreSQLDriver()
        driver.connect(connectionConfig(container))

        val result = driver.executeQuery("SELECT 1 as one")
        assertTrue(result is QueryResult.Success)
        assertEquals(listOf("one"), result.columnNames)
        assertEquals("1", result.rows.single()["one"])

        driver.disconnect()
    }

    @Test
    fun testExecuteQuerySelectUsersColumns() = kotlinx.coroutines.runBlocking {
        if (!dockerAvailable) return@runBlocking

        val container = postgresContainer!!
        val driver = PostgreSQLDriver()
        driver.connect(connectionConfig(container))

        val result = driver.executeQuery("SELECT * FROM users ORDER BY id LIMIT 10")
        assertTrue(result is QueryResult.Success)

        assertTrue("id" in result.columnNames)
        assertTrue("name" in result.columnNames)
        assertTrue("email" in result.columnNames)
        assertEquals("1", result.rows.first()["id"])
        assertEquals("Alice", result.rows.first()["name"])

        driver.disconnect()
    }

    @Test
    fun testMetadataTablesColumnsForeignKeysAndSchema() = kotlinx.coroutines.runBlocking {
        if (!dockerAvailable) return@runBlocking

        val container = postgresContainer!!
        val driver = PostgreSQLDriver()
        driver.connect(connectionConfig(container))

        val tables = driver.getTables()
        assertTrue("users" in tables)
        assertTrue("orders" in tables)

        val userColumns = driver.getColumns("users")
        assertTrue(userColumns.any { it.name == "id" })
        assertTrue(userColumns.any { it.name == "name" })
        assertTrue(userColumns.any { it.name == "email" })

        val foreignKeys = driver.getForeignKeys()
        assertTrue(
            foreignKeys.any {
                it.fromTable == "orders" &&
                    it.fromColumn == "user_id" &&
                    it.toTable == "users" &&
                    it.toColumn == "id"
            },
            "Expected orders.user_id -> users.id foreign key",
        )

        val schema = driver.getSchema()
        assertTrue(schema.tables.any { it.name == "users" })
        assertTrue(schema.tables.any { it.name == "orders" })
        assertTrue(schema.foreignKeys.isNotEmpty())

        driver.disconnect()
    }
}

private fun connectionConfig(container: PostgreSQLContainer<*>): ConnectionConfig {
    val profile =
        ConnectionProfile(
            id = "test-postgres-driver",
            name = "Test PostgreSQL Driver",
            type = DatabaseType.PostgreSQL,
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            username = container.username,
            encryptedPassword = "",
            options = mapOf("password" to container.password),
        )

    return ConnectionConfig(
        profile = profile,
        connectionTimeoutSeconds = 30,
        queryTimeoutSeconds = 60,
    )
}

private fun seedSchema(container: PostgreSQLContainer<*>) {
    container.createConnection("").use { conn ->
        conn.autoCommit = true
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT
                );
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS orders (
                    id SERIAL PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    total_cents INTEGER NOT NULL,
                    CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id)
                );
                """.trimIndent(),
            )
            st.execute("TRUNCATE TABLE orders RESTART IDENTITY;")
            st.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE;")
            st.execute("INSERT INTO users(name, email) VALUES ('Alice', 'alice@example.com'), ('Bob', 'bob@example.com');")
            st.execute("INSERT INTO orders(user_id, total_cents) VALUES (1, 1000), (1, 2500), (2, 3000);")
        }
    }
}
