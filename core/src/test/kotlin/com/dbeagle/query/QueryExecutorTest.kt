package com.dbeagle.query

import com.dbeagle.driver.DatabaseCapability
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.settings.AppPreferences
import com.dbeagle.settings.AppSettings
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class QueryExecutorTest {
    @BeforeTest
    fun resetSettings() {
        AppPreferences.save(AppSettings())
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
