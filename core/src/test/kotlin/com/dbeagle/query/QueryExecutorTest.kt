package com.dbeagle.query

import com.dbeagle.driver.DatabaseCapability
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.logging.QueryLogService
import com.dbeagle.logging.QueryStatus
import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.TableMetadata
import com.dbeagle.settings.AppSettings
import com.dbeagle.settings.SettingsProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryExecutorTest {
    private val tempDir: File = File(System.getProperty("java.io.tmpdir"), "querylog-test-executor")

    @BeforeAll
    fun setupAll() {
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        setupTestLogFile()
    }

    @AfterAll
    fun teardownAll() {
        QueryLogService.testLogFile = null
        tempDir.deleteRecursively()
    }

    @BeforeTest
    fun resetSettings() {
        setupTestLogFile()
        QueryLogService.clearLogs()

        val settings = SettingsProvider.createAppSettings()
        settings.putInt("resultLimit", AppSettings.DEFAULT_RESULT_LIMIT)
        settings.putInt("queryTimeoutSeconds", AppSettings.DEFAULT_QUERY_TIMEOUT_SECONDS)
        settings.putInt("connectionTimeoutSeconds", AppSettings.DEFAULT_CONNECTION_TIMEOUT_SECONDS)
        settings.putInt("maxConnections", AppSettings.DEFAULT_MAX_CONNECTIONS)
        settings.remove("darkMode")
    }

    private fun setupTestLogFile() {
        val dir = File(tempDir, ".dbeagle")
        dir.mkdirs()
        QueryLogService.testLogFile = File(dir, "query.log")
    }

    @Test
    fun `paginates 5000 rows without loading all at once`() = runBlocking {
        val driver = FakePagingDriver(totalRows = 5000)
        val executor = QueryExecutor(driver)

        val first = executor.execute("SELECT * FROM big_table") as QueryResult.Success
        assertEquals(1000, first.rows.size)
        assertNotNull(first.resultSet)
        assertTrue(first.resultSet.hasMore())
        assertEquals(1001, driver.lastRequestedLimit)
        assertEquals(0, driver.lastRequestedOffset)

        var current: QueryResult.Success? = first
        var pages = 1
        while (current?.resultSet?.hasMore() == true) {
            current = current.resultSet.fetchNext()
            pages++
        }

        assertEquals(5, pages)
        assertNotNull(current)
        assertEquals(1000, current.rows.size)
        assertEquals(current.resultSet?.hasMore(), false)
        assertEquals(1001, driver.lastRequestedLimit)
        assertEquals(4000, driver.lastRequestedOffset)
    }

    @Test
    fun `fetchNext returns subsequent pages and ends with hasMore false`() = runBlocking {
        val driver = FakePagingDriver(totalRows = 5000)
        val executor = QueryExecutor(driver)

        val first = executor.execute("SELECT * FROM big_table") as QueryResult.Success
        val second = first.resultSet!!.fetchNext()
        assertNotNull(second)
        assertEquals(1000, second.rows.size)
        assertEquals("1000", second.rows.first()["id"])
        assertTrue(second.resultSet!!.hasMore())

        var page: QueryResult.Success = second
        repeat(3) {
            page = page.resultSet!!.fetchNext()!!
        }
        assertEquals("4000", page.rows.first()["id"])
        assertEquals(page.resultSet?.hasMore(), false)
        assertNull(page.resultSet?.fetchNext())
    }

    @Test
    fun `non select delegates to driver and has null resultSet`() = runBlocking {
        val driver = object : DatabaseDriver {
            override suspend fun connect(config: ConnectionConfig) = Unit
            override suspend fun disconnect() = Unit
            override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult = if (sql.trimStart().startsWith("update", ignoreCase = true)) {
                QueryResult.Success(columnNames = emptyList(), rows = emptyList())
            } else {
                QueryResult.Error("no")
            }

            override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(tables = emptyList())
            override suspend fun getTables(): List<String> = emptyList()
            override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()
            override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
            override suspend fun testConnection(): Boolean = true
            override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
            override fun getName(): String = "fake"
        }

        val executor = QueryExecutor(driver)
        val result = executor.execute("UPDATE t SET a = 1") as QueryResult.Success
        assertNull(result.resultSet)
    }

    @Test
    fun `getSchema logs SCHEMA_METADATA with SUCCESS status`() = runBlocking {
        val driver = object : DatabaseDriver {
            override suspend fun connect(config: ConnectionConfig) = Unit
            override suspend fun disconnect() = Unit
            override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult = QueryResult.Error("not used")
            override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(
                tables = listOf(
                    TableMetadata(name = "users", schema = "public", columns = emptyList()),
                    TableMetadata(name = "posts", schema = "public", columns = emptyList()),
                ),
            )
            override suspend fun getTables(): List<String> = emptyList()
            override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()
            override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
            override suspend fun testConnection(): Boolean = true
            override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
            override fun getName(): String = "fake"
        }

        val executor = QueryExecutor(driver)
        val schema = executor.getSchema()

        assertEquals(2, schema.tables.size)

        val logs = QueryLogService.getLogs().filter { it.sql == "SCHEMA_METADATA" && it.status == QueryStatus.SUCCESS }
        assertTrue(logs.isNotEmpty(), "Expected at least one SCHEMA_METADATA log entry")

        val logEntry = logs.last()
        assertEquals("SCHEMA_METADATA", logEntry.sql)
        assertEquals(QueryStatus.SUCCESS, logEntry.status)
        assertEquals(2, logEntry.rowCount)
        assertNull(logEntry.errorMessage)
        assertTrue(logEntry.durationMs >= 0)
    }

    @Test
    fun `getSchema logs SCHEMA_METADATA with ERROR status on failure`() = runBlocking {
        val driver = object : DatabaseDriver {
            override suspend fun connect(config: ConnectionConfig) = Unit
            override suspend fun disconnect() = Unit
            override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult = QueryResult.Error("not used")
            override suspend fun getSchema(): SchemaMetadata = throw RuntimeException("Schema fetch failed")
            override suspend fun getTables(): List<String> = emptyList()
            override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()
            override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
            override suspend fun testConnection(): Boolean = true
            override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
            override fun getName(): String = "fake"
        }

        val executor = QueryExecutor(driver)
        var exception: Exception? = null
        try {
            executor.getSchema()
        } catch (e: Exception) {
            exception = e
        }

        assertNotNull(exception)

        val logs = QueryLogService.getLogs().filter { it.sql == "SCHEMA_METADATA" && it.status == QueryStatus.ERROR }
        assertTrue(logs.isNotEmpty(), "Expected at least one SCHEMA_METADATA error log entry")

        val logEntry = logs.last()
        assertEquals("SCHEMA_METADATA", logEntry.sql)
        assertEquals(QueryStatus.ERROR, logEntry.status)
        assertNotNull(logEntry.errorMessage)
        assertTrue(logEntry.errorMessage!!.contains("Schema fetch failed"))
        assertTrue(logEntry.durationMs >= 0)
    }

    @Test
    fun `getColumns logs GET_COLUMNS with table name and SUCCESS status`() = runBlocking {
        val driver = object : DatabaseDriver {
            override suspend fun connect(config: ConnectionConfig) = Unit
            override suspend fun disconnect() = Unit
            override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult = QueryResult.Error("not used")
            override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(tables = emptyList())
            override suspend fun getTables(): List<String> = emptyList()
            override suspend fun getColumns(table: String): List<ColumnMetadata> = listOf(
                ColumnMetadata(name = "id", type = "INTEGER", nullable = false),
                ColumnMetadata(name = "name", type = "TEXT", nullable = false),
                ColumnMetadata(name = "email", type = "TEXT", nullable = true),
            )
            override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
            override suspend fun testConnection(): Boolean = true
            override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
            override fun getName(): String = "fake"
        }

        val executor = QueryExecutor(driver)
        val columns = executor.getColumns("users")

        assertEquals(3, columns.size)

        val logs = QueryLogService.getLogs().filter { it.sql == "GET_COLUMNS(users)" && it.status == QueryStatus.SUCCESS }
        assertTrue(logs.isNotEmpty(), "Expected at least one GET_COLUMNS(users) log entry")

        val logEntry = logs.last()
        assertEquals("GET_COLUMNS(users)", logEntry.sql)
        assertEquals(QueryStatus.SUCCESS, logEntry.status)
        assertEquals(3, logEntry.rowCount)
        assertNull(logEntry.errorMessage)
        assertTrue(logEntry.durationMs >= 0)
    }

    @Test
    fun `getColumns logs GET_COLUMNS with ERROR status on failure`() = runBlocking {
        val driver = object : DatabaseDriver {
            override suspend fun connect(config: ConnectionConfig) = Unit
            override suspend fun disconnect() = Unit
            override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult = QueryResult.Error("not used")
            override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(tables = emptyList())
            override suspend fun getTables(): List<String> = emptyList()
            override suspend fun getColumns(table: String): List<ColumnMetadata> = throw RuntimeException("Table not found: $table")
            override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
            override suspend fun testConnection(): Boolean = true
            override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
            override fun getName(): String = "fake"
        }

        val executor = QueryExecutor(driver)
        var exception: Exception? = null
        try {
            executor.getColumns("missing_table")
        } catch (e: Exception) {
            exception = e
        }

        assertNotNull(exception)

        val logs = QueryLogService.getLogs().filter { it.sql == "GET_COLUMNS(missing_table)" && it.status == QueryStatus.ERROR }
        assertTrue(logs.isNotEmpty(), "Expected at least one GET_COLUMNS(missing_table) error log entry")

        val logEntry = logs.last()
        assertEquals("GET_COLUMNS(missing_table)", logEntry.sql)
        assertEquals(QueryStatus.ERROR, logEntry.status)
        assertNotNull(logEntry.errorMessage)
        assertTrue(logEntry.errorMessage!!.contains("Table not found: missing_table"))
        assertTrue(logEntry.durationMs >= 0)
    }

    @Test
    fun `getTables logs GET_TABLES with SUCCESS status`() = runBlocking {
        val driver = object : DatabaseDriver {
            override suspend fun connect(config: ConnectionConfig) = Unit
            override suspend fun disconnect() = Unit
            override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult = QueryResult.Error("not used")
            override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(tables = emptyList())
            override suspend fun getTables(): List<String> = listOf("users", "posts", "comments", "categories")
            override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()
            override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
            override suspend fun testConnection(): Boolean = true
            override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
            override fun getName(): String = "fake"
        }

        val executor = QueryExecutor(driver)
        val tables = executor.getTables()

        assertEquals(4, tables.size)

        val logs = QueryLogService.getLogs().filter { it.sql == "GET_TABLES" && it.status == QueryStatus.SUCCESS }
        assertTrue(logs.isNotEmpty(), "Expected at least one GET_TABLES log entry")

        val logEntry = logs.last()
        assertEquals("GET_TABLES", logEntry.sql)
        assertEquals(QueryStatus.SUCCESS, logEntry.status)
        assertEquals(4, logEntry.rowCount)
        assertNull(logEntry.errorMessage)
        assertTrue(logEntry.durationMs >= 0)
    }

    @Test
    fun `getTables logs GET_TABLES with ERROR status on failure`() = runBlocking {
        val driver = object : DatabaseDriver {
            override suspend fun connect(config: ConnectionConfig) = Unit
            override suspend fun disconnect() = Unit
            override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult = QueryResult.Error("not used")
            override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(tables = emptyList())
            override suspend fun getTables(): List<String> = throw RuntimeException("Failed to retrieve tables")
            override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()
            override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
            override suspend fun testConnection(): Boolean = true
            override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
            override fun getName(): String = "fake"
        }

        val executor = QueryExecutor(driver)
        var exception: Exception? = null
        try {
            executor.getTables()
        } catch (e: Exception) {
            exception = e
        }

        assertNotNull(exception)

        val logs = QueryLogService.getLogs().filter { it.sql == "GET_TABLES" && it.status == QueryStatus.ERROR }
        assertTrue(logs.isNotEmpty(), "Expected at least one GET_TABLES error log entry")

        val logEntry = logs.last()
        assertEquals("GET_TABLES", logEntry.sql)
        assertEquals(QueryStatus.ERROR, logEntry.status)
        assertNotNull(logEntry.errorMessage)
        assertTrue(logEntry.errorMessage!!.contains("Failed to retrieve tables"))
        assertTrue(logEntry.durationMs >= 0)
    }
}

private class FakePagingDriver(private val totalRows: Int) : DatabaseDriver {
    var lastRequestedLimit: Int = -1
    var lastRequestedOffset: Int = -1

    override suspend fun connect(config: ConnectionConfig) = Unit
    override suspend fun disconnect() = Unit

    override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult {
        if (!sql.contains("LIMIT ? OFFSET ?")) {
            return QueryResult.Error("unexpected sql")
        }
        val limit = (params[params.size - 2] as Number).toInt()
        val offset = (params[params.size - 1] as Number).toInt()
        lastRequestedLimit = limit
        lastRequestedOffset = offset

        val endExclusive = minOf(totalRows, offset + limit)
        val rows = (offset until endExclusive).map { i -> mapOf("id" to i.toString()) }
        return QueryResult.Success(columnNames = listOf("id"), rows = rows)
    }

    override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(tables = emptyList())
    override suspend fun getTables(): List<String> = emptyList()
    override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()
    override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()
    override suspend fun testConnection(): Boolean = true
    override fun getCapabilities(): Set<DatabaseCapability> = emptySet()
    override fun getName(): String = "FakePagingDriver"
}
