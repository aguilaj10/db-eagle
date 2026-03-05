package com.dbeagle.driver

import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.TableMetadata
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseDriverTest {
    private val testConfig =
        ConnectionConfig(
            profile =
            ConnectionProfile(
                id = "test-profile",
                name = "Test Database",
                type = DatabaseType.PostgreSQL,
                host = "localhost",
                port = 5432,
                database = "testdb",
                username = "testuser",
                encryptedPassword = "encrypted-password",
                options = emptyMap(),
            ),
        )

    @Test
    fun testMockDriverImplementation() = runBlocking {
        val driver = MockDatabaseDriver()

        driver.connect(testConfig)
        assertTrue(driver.isConnected, "Driver should be connected after connect()")

        val testResult = driver.executeQuery("SELECT 1", emptyList())
        assertTrue(testResult is QueryResult.Success, "Query should succeed")

        assertEquals(listOf("column1"), testResult.columnNames)
        assertEquals(1, testResult.rows.size)

        val isAlive = driver.testConnection()
        assertTrue(isAlive, "Test connection should return true")

        driver.disconnect()
        assertFalse(driver.isConnected, "Driver should be disconnected after disconnect()")
    }

    @Test
    fun testCapabilityExposure() = runBlocking {
        val driver = MockDatabaseDriver()
        val capabilities = driver.getCapabilities()

        assertTrue(DatabaseCapability.Transactions in capabilities, "Should support transactions")
        assertTrue(DatabaseCapability.PreparedStatements in capabilities, "Should support prepared statements")
        assertTrue(DatabaseCapability.ForeignKeys in capabilities, "Should support foreign keys")
    }

    @Test
    fun testSchemaMetadataRetrieval() = runBlocking {
        val driver = MockDatabaseDriver()
        driver.connect(testConfig)

        val schema = driver.getSchema()
        assertEquals(1, schema.tables.size, "Should return one table")
        assertEquals("mock_table", schema.tables[0].name)

        val tables = driver.getTables()
        assertEquals(listOf("mock_table"), tables)
    }

    @Test
    fun testColumnMetadataRetrieval() = runBlocking {
        val driver = MockDatabaseDriver()
        driver.connect(testConfig)

        val columns = driver.getColumns("mock_table")
        assertEquals(2, columns.size)
        assertEquals("id", columns[0].name)
        assertEquals("name", columns[1].name)
    }

    @Test
    fun testForeignKeyRetrieval() = runBlocking {
        val driver = MockDatabaseDriver()
        driver.connect(testConfig)

        val foreignKeys = driver.getForeignKeys()
        assertEquals(1, foreignKeys.size)
        assertEquals("mock_table", foreignKeys[0].fromTable)
        assertEquals("other_table", foreignKeys[0].toTable)
    }

    @Test
    fun testDriverName() {
        val driver = MockDatabaseDriver()
        assertEquals("Mock Driver", driver.getName())
    }
}

class MockDatabaseDriver : DatabaseDriver {
    var isConnected = false

    override suspend fun connect(config: ConnectionConfig) {
        isConnected = true
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun executeQuery(
        sql: String,
        params: List<Any>,
    ): QueryResult = QueryResult.Success(
        columnNames = listOf("column1"),
        rows = listOf(mapOf("column1" to "value1")),
    )

    override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(
        tables =
        listOf(
            TableMetadata(
                name = "mock_table",
                schema = "public",
                columns =
                listOf(
                    ColumnMetadata("id", "INTEGER", false),
                    ColumnMetadata("name", "VARCHAR", true),
                ),
                primaryKey = listOf("id"),
            ),
        ),
        foreignKeys =
        listOf(
            ForeignKeyRelationship(
                fromTable = "mock_table",
                fromColumn = "id",
                toTable = "other_table",
                toColumn = "id",
            ),
        ),
    )

    override suspend fun getTables(): List<String> = listOf("mock_table")

    override suspend fun getColumns(table: String): List<ColumnMetadata> = listOf(
        ColumnMetadata("id", "INTEGER", false),
        ColumnMetadata("name", "VARCHAR", true),
    )

    override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = listOf(
        ForeignKeyRelationship(
            fromTable = "mock_table",
            fromColumn = "id",
            toTable = "other_table",
            toColumn = "id",
        ),
    )

    override suspend fun testConnection(): Boolean = isConnected

    override fun getCapabilities(): Set<DatabaseCapability> = setOf(
        DatabaseCapability.Transactions,
        DatabaseCapability.PreparedStatements,
        DatabaseCapability.ForeignKeys,
        DatabaseCapability.Indexes,
        DatabaseCapability.Schemas,
    )

    override fun getName(): String = "Mock Driver"
}
