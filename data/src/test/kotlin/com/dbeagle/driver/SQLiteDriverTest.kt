package com.dbeagle.driver

import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.model.QueryResult
import com.dbeagle.query.QueryExecutor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SQLiteDriverTest {
    private lateinit var driver: SQLiteDriver

    @BeforeTest
    fun setup() {
        runBlocking {
            driver = SQLiteDriver()
            driver.connect(connectionConfig())

            driver.executeQuery(
                """
                CREATE TABLE users (
                  id INTEGER PRIMARY KEY,
                  name TEXT NOT NULL,
                  email TEXT
                );
                """.trimIndent()
            )

            driver.executeQuery(
                """
                CREATE TABLE orders (
                  id INTEGER PRIMARY KEY,
                  user_id INTEGER NOT NULL,
                  total_cents INTEGER NOT NULL,
                  FOREIGN KEY(user_id) REFERENCES users(id)
                );
                """.trimIndent()
            )

            driver.executeQuery(
                "INSERT INTO users(id, name, email) VALUES (1, 'Alice', 'alice@example.com'), (2, 'Bob', 'bob@example.com');"
            )
            driver.executeQuery(
                "INSERT INTO orders(id, user_id, total_cents) VALUES (1, 1, 1000), (2, 1, 2500), (3, 2, 3000);"
            )
        }
    }

    @AfterTest
    fun teardown() = runBlocking {
        driver.disconnect()
    }

    @Test
    fun testExecuteQuerySelect1() = runBlocking {
        val result = driver.executeQuery("SELECT 1 as one")
        assertTrue(result is QueryResult.Success)

        assertEquals(listOf("one"), result.columnNames)
        assertEquals("1", result.rows.single()["one"])
    }

    @Test
    fun testPreparedStatementParams() = runBlocking {
        val result = driver.executeQuery(
            sql = "SELECT name FROM users WHERE id = ?",
            params = listOf(1)
        )
        assertTrue(result is QueryResult.Success)

        assertEquals(listOf("name"), result.columnNames)
        assertEquals("Alice", result.rows.single()["name"])
    }

    @Test
    fun testUpdatePersistsViaQueryExecutorWithParams() = runBlocking {
        val before = driver.executeQuery(
            sql = "SELECT name FROM users WHERE id = ?",
            params = listOf(1)
        )
        assertTrue(before is QueryResult.Success)
        assertEquals("Alice", before.rows.single()["name"])

        val update = QueryExecutor(driver).execute(
            sql = "UPDATE users SET name = ? WHERE id = ?",
            params = listOf("AliceUpdated", 1)
        )
        assertTrue(update is QueryResult.Success)

        assertEquals(listOf("updatedCount"), update.columnNames)
        assertEquals("1", update.rows.single()["updatedCount"])

        val after = driver.executeQuery(
            sql = "SELECT name FROM users WHERE id = ?",
            params = listOf(1)
        )
        assertTrue(after is QueryResult.Success)
        assertEquals("AliceUpdated", after.rows.single()["name"])
    }

    @Test
    fun testMetadataTablesColumnsForeignKeysAndSchema() = runBlocking {
        val tables = driver.getTables()
        assertTrue("users" in tables)
        assertTrue("orders" in tables)
        assertTrue(tables.none { it.startsWith("sqlite_") })

        val userColumns = driver.getColumns("users")
        assertTrue(userColumns.any { it.name == "id" })
        assertTrue(userColumns.any { it.name == "name" && it.nullable.not() })

        val foreignKeys = driver.getForeignKeys()
        assertTrue(
            foreignKeys.any {
                it.fromTable == "orders" &&
                    it.fromColumn == "user_id" &&
                    it.toTable == "users" &&
                    it.toColumn == "id"
            },
            "Expected orders.user_id -> users.id foreign key"
        )

        val schema = driver.getSchema()
        assertTrue(schema.tables.any { it.name == "users" })
        assertTrue(schema.tables.any { it.name == "orders" })
        assertTrue(schema.foreignKeys.isNotEmpty())
    }

    @Test
    fun testCapabilitiesForeignKeysEnabled() {
        val caps = driver.getCapabilities()
        assertTrue(DatabaseCapability.Transactions in caps)
        assertTrue(DatabaseCapability.PreparedStatements in caps)
        assertTrue(DatabaseCapability.ForeignKeys in caps)
    }
}

private fun connectionConfig(): ConnectionConfig {
    val profile = ConnectionProfile(
        id = "test-sqlite-driver",
        name = "Test SQLite Driver",
        type = DatabaseType.SQLite,
        host = "",
        port = 0,
        database = ":memory:",
        username = "",
        encryptedPassword = "",
        options = emptyMap()
    )

    return ConnectionConfig(
        profile = profile,
        connectionTimeoutSeconds = 30,
        queryTimeoutSeconds = 60
    )
}
