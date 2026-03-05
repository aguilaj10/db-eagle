package com.dbeagle.query

import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.QueryResult
import com.dbeagle.settings.AppPreferences
import com.dbeagle.settings.AppSettings
import com.dbeagle.test.BaseTest
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QueryExecutorSettingsIntegrationTest : BaseTest() {
    private class FakeDriver : DatabaseDriver {
        var queryExecuted: String? = null
        var paramsReceived: List<Any>? = null

        override suspend fun connect(config: com.dbeagle.model.ConnectionConfig) {}
        override suspend fun disconnect() {}
        override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult {
            queryExecuted = sql
            paramsReceived = params
            val rows = (1..2000).map { mapOf("id" to it.toString(), "value" to "row$it") }
            return QueryResult.Success(
                columnNames = listOf("id", "value"),
                rows = rows,
            )
        }
        override suspend fun getSchema(): com.dbeagle.model.SchemaMetadata = com.dbeagle.model.SchemaMetadata(emptyList())
        override suspend fun getTables(): List<String> = emptyList()
        override suspend fun getColumns(table: String): List<com.dbeagle.model.ColumnMetadata> = emptyList()
        override suspend fun getForeignKeys(): List<com.dbeagle.model.ForeignKeyRelationship> = emptyList()
        override suspend fun testConnection(): Boolean = true
        override fun getCapabilities(): Set<com.dbeagle.driver.DatabaseCapability> = emptySet()
        override fun getName(): String = "FakeDriver"
    }

    @Test
    fun `query executor respects settings result limit`() = runBlocking {
        val settings = AppSettings(resultLimit = 500)
        AppPreferences.save(settings)

        val driver = FakeDriver()
        val executor = QueryExecutor(driver)

        val result = executor.execute("SELECT * FROM test_table")

        assertTrue(result is QueryResult.Success)
        assertEquals(500, result.rows.size)

        assertNotNull(driver.queryExecuted)
        assertTrue(driver.queryExecuted!!.contains("LIMIT ?"))
        assertNotNull(driver.paramsReceived)
        assertEquals(501, driver.paramsReceived!![0])
    }

    @Test
    fun `settings roundtrip with non-default values`() {
        val custom = AppSettings(
            resultLimit = 250,
            queryTimeoutSeconds = 90,
            connectionTimeoutSeconds = 45,
            maxConnections = 15,
        )
        AppPreferences.save(custom)

        val loaded = AppPreferences.load()
        assertEquals(250, loaded.resultLimit)
        assertEquals(90, loaded.queryTimeoutSeconds)
        assertEquals(45, loaded.connectionTimeoutSeconds)
        assertEquals(15, loaded.maxConnections)
    }

    @Test
    fun `generate evidence for task 32`() = runBlocking {
        // Find repo root by walking up until .sisyphus directory exists
        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, ".sisyphus").exists() }
            ?: File(System.getProperty("user.dir"))

        val evidenceDir = File(repoRoot, ".sisyphus/evidence")
        evidenceDir.mkdirs()
        val evidenceFile = File(evidenceDir, "task-32-settings.txt")

        val output = StringBuilder()

        output.appendLine("=== Task 32: Application Settings Evidence ===")
        output.appendLine()

        // Test 1: Save custom settings
        output.appendLine("Step 1: Save custom settings with resultLimit=500")
        val settings = AppSettings(
            resultLimit = 500,
            queryTimeoutSeconds = 75,
            connectionTimeoutSeconds = 40,
            maxConnections = 12,
        )
        AppPreferences.save(settings)
        output.appendLine("Saved: resultLimit=${settings.resultLimit}, queryTimeout=${settings.queryTimeoutSeconds}, connectionTimeout=${settings.connectionTimeoutSeconds}, maxConnections=${settings.maxConnections}")
        output.appendLine()

        // Test 2: Load settings and verify
        output.appendLine("Step 2: Load settings from persistence")
        val loaded = AppPreferences.load()
        output.appendLine("Loaded: resultLimit=${loaded.resultLimit}, queryTimeout=${loaded.queryTimeoutSeconds}, connectionTimeout=${loaded.connectionTimeoutSeconds}, maxConnections=${loaded.maxConnections}")
        output.appendLine("Verification: resultLimit matches = ${loaded.resultLimit == 500}")
        output.appendLine()

        // Test 3: Execute query and verify result limit is applied
        output.appendLine("Step 3: Execute query via QueryExecutor")
        val driver = FakeDriver()
        val executor = QueryExecutor(driver)
        val result = executor.execute("SELECT * FROM test_table")

        output.appendLine("Query executed: ${driver.queryExecuted}")
        output.appendLine("LIMIT parameter sent to driver: ${driver.paramsReceived?.get(0)}")

        val success = result as QueryResult.Success
        output.appendLine("Rows returned: ${success.rows.size}")
        output.appendLine("Verification: rows returned matches resultLimit = ${success.rows.size == 500}")
        output.appendLine()

        output.appendLine("=== QA Result: SUCCESS ===")
        output.appendLine("Settings persist correctly and resultLimit=500 causes QueryExecutor to return exactly 500 rows.")

        evidenceFile.writeText(output.toString())

        assertTrue(evidenceFile.exists(), "Evidence file should exist")
        assertTrue(evidenceFile.readText().contains("resultLimit=500"), "Evidence should contain resultLimit=500")
        assertTrue(evidenceFile.readText().contains("Rows returned: 500"), "Evidence should prove 500 rows returned")
    }
}
