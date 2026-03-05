package com.dbeagle.viewmodel

import com.dbeagle.driver.DatabaseCapability
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.history.QueryHistoryRepository
import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryHistoryEntry
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryEditorViewModelTest {

    /**
     * Fake QueryHistoryRepository for testing without file system dependencies.
     */
    private class FakeQueryHistoryRepository : QueryHistoryRepository {
        private val entries = mutableListOf<QueryHistoryEntry>()

        override fun add(entry: QueryHistoryEntry) {
            entries.add(0, entry) // Add at front (most recent first)
        }

        override fun getAll(): List<QueryHistoryEntry> = entries.toList()

        override fun clear() {
            entries.clear()
        }
    }

    private fun createTestRepository() = FakeQueryHistoryRepository()

    /**
     * Fake DatabaseDriver for testing query execution.
     */
    private class FakeDriver(
        private val queryResult: QueryResult = QueryResult.Success(
            columnNames = listOf("id", "name"),
            rows = listOf(mapOf("id" to "1", "name" to "test")),
        ),
        private val delayMs: Long = 0,
    ) : DatabaseDriver {
        var executeQueryCallCount = 0
        var lastSql: String? = null
        var lastParams: List<Any>? = null

        override suspend fun connect(config: ConnectionConfig) {}

        override suspend fun disconnect() {}

        override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult {
            executeQueryCallCount++
            lastSql = sql
            lastParams = params
            if (delayMs > 0) delay(delayMs)
            return queryResult
        }

        override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(
            tables = emptyList(),
            views = emptyList(),
            indexes = emptyList(),
            foreignKeys = emptyList(),
        )

        override suspend fun getTables(): List<String> = emptyList()

        override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()

        override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()

        override suspend fun testConnection(): Boolean = true

        override fun getCapabilities(): Set<DatabaseCapability> = emptySet()

        override fun getName(): String = "FakeDriver"
    }

    @Test
    fun initialState_isCorrect() {
        val viewModel = QueryEditorViewModel(createTestRepository())

        val state = viewModel.uiState.value

        assertFalse(state.isRunning, "Initial isRunning should be false")
        assertNull(state.editError, "Initial editError should be null")
        assertFalse(state.showExportDialog, "Initial showExportDialog should be false")
    }

    @Test
    fun showExportDialog_updatesState() {
        val viewModel = QueryEditorViewModel(createTestRepository())

        assertFalse(viewModel.uiState.value.showExportDialog)

        viewModel.showExportDialog()

        assertTrue(viewModel.uiState.value.showExportDialog, "showExportDialog should be true after calling showExportDialog()")
    }

    @Test
    fun hideExportDialog_updatesState() {
        val viewModel = QueryEditorViewModel(createTestRepository())

        viewModel.showExportDialog()
        assertTrue(viewModel.uiState.value.showExportDialog)

        viewModel.hideExportDialog()

        assertFalse(viewModel.uiState.value.showExportDialog, "showExportDialog should be false after calling hideExportDialog()")
    }

    @Test
    fun clearEditError_clearsError() {
        val viewModel = QueryEditorViewModel(createTestRepository())

        // Manually set an error by updating the state flow
        viewModel.uiState.value.let { state ->
            // We need to trigger an inline edit error to test clearEditError
            // Since we can't directly set the error, we'll use executeInlineEdit with invalid conditions
        }

        // Instead, let's test that clearEditError works when there is an error
        // We'll test this through executeInlineEdit which sets editError
        runBlocking {
            val driver = FakeDriver()
            val result = viewModel.executeInlineEdit(
                driver = driver,
                lastExecutedSql = null, // This will cause an error
                columns = listOf("id", "name"),
                columnName = "name",
                newValue = "updated",
                rowSnapshot = listOf("1", "test"),
                onStatusChanged = {},
            )

            assertTrue(result.isFailure)
            assertNotNull(viewModel.uiState.value.editError, "editError should be set after failed inline edit")

            viewModel.clearEditError()

            assertNull(viewModel.uiState.value.editError, "editError should be null after calling clearEditError()")
        }
    }

    @Test
    fun executeQuery_updatesIsRunningState() = runBlocking {
        val viewModel = QueryEditorViewModel(createTestRepository())
        val driver = FakeDriver(delayMs = 50)

        var isRunningDuringExecution = false
        var statusMessages = mutableListOf<String>()

        assertFalse(viewModel.uiState.value.isRunning, "Should not be running initially")

        // Start query execution in background
        viewModel.executeQuery(
            sqlToRun = "SELECT * FROM users",
            driver = driver,
            profileId = "test-profile",
            profileName = "Test DB",
            onStatusChanged = { status ->
                statusMessages.add(status)
                if (status.contains("Running query")) {
                    isRunningDuringExecution = viewModel.uiState.value.isRunning
                }
            },
            onQuerySuccess = { _, _ -> },
            onQueryError = { _, _ -> },
        )

        // Check immediately after starting
        delay(10) // Small delay to let the coroutine start
        assertTrue(viewModel.uiState.value.isRunning, "Should be running after executeQuery starts")

        // Wait for query to complete
        delay(100)

        assertFalse(viewModel.uiState.value.isRunning, "Should not be running after query completes")
        assertTrue(isRunningDuringExecution, "isRunning should have been true during execution")
    }

    @Test
    fun executeQuery_successfulQuery_recordsHistory() = runBlocking {
        val testRepository = createTestRepository()
        val viewModel = QueryEditorViewModel(testRepository)
        val driver = FakeDriver()

        var successCalled = false
        var queryResult: QueryResult.Success? = null
        var durationMs: Long = 0

        viewModel.executeQuery(
            sqlToRun = "SELECT * FROM users",
            driver = driver,
            profileId = "test-profile",
            profileName = "Test DB",
            onStatusChanged = {},
            onQuerySuccess = { result, duration ->
                successCalled = true
                queryResult = result
                durationMs = duration
            },
            onQueryError = { _, _ -> },
        )

        delay(200) // Wait for async execution

        assertTrue(successCalled, "onQuerySuccess should be called")
        assertNotNull(queryResult, "Query result should not be null")
        assertTrue(durationMs >= 0, "Duration should be non-negative")

        val historyEntry = testRepository.getAll().firstOrNull()
        assertNotNull(historyEntry, "History entry should be recorded")
        assertEquals("SELECT * FROM users", historyEntry.query)
        assertEquals("test-profile", historyEntry.connectionProfileId)
        assertTrue(historyEntry.durationMs >= 0, "History duration should be non-negative")
    }

    @Test
    fun executeQuery_errorResult_recordsHistory() = runBlocking {
        val testRepository = createTestRepository()
        val viewModel = QueryEditorViewModel(testRepository)
        val driver = FakeDriver(queryResult = QueryResult.Error("Syntax error"))

        var errorCalled = false
        var errorMessage: String? = null

        viewModel.executeQuery(
            sqlToRun = "SELECT * FROM invalid",
            driver = driver,
            profileId = "test-profile",
            profileName = "Test DB",
            onStatusChanged = {},
            onQuerySuccess = { _, _ -> },
            onQueryError = { message, _ ->
                errorCalled = true
                errorMessage = message
            },
        )

        // Wait for query to complete (isRunning becomes false)
        var attempts = 0
        while (viewModel.uiState.value.isRunning && attempts < 50) {
            delay(20)
            attempts++
        }

        delay(100)

        assertTrue(errorCalled, "onQueryError should be called")
        assertEquals("Syntax error", errorMessage)

        val historyEntry = testRepository.getAll().firstOrNull()
        assertNotNull(historyEntry, "History entry should be recorded even on error")
        assertEquals("SELECT * FROM invalid", historyEntry.query)
        assertEquals("test-profile", historyEntry.connectionProfileId)
    }

    @Test
    fun cancelQuery_stopsRunningQuery() = runBlocking {
        val viewModel = QueryEditorViewModel(createTestRepository())
        val driver = FakeDriver(delayMs = 200) // Long running query

        var statusChangedMessage: String? = null
        var successCalled = false

        viewModel.executeQuery(
            sqlToRun = "SELECT * FROM users",
            driver = driver,
            profileId = "test-profile",
            profileName = "Test DB",
            onStatusChanged = {},
            onQuerySuccess = { _, _ ->
                successCalled = true
            },
            onQueryError = { _, _ -> },
        )

        delay(50) // Wait for query to start
        assertTrue(viewModel.uiState.value.isRunning, "Should be running before cancel")

        viewModel.cancelQuery { status ->
            statusChangedMessage = status
        }

        assertFalse(viewModel.uiState.value.isRunning, "Should not be running after cancel")
        assertEquals("Status: Query canceled", statusChangedMessage)

        delay(200) // Wait for original query to finish
        assertFalse(successCalled, "onQuerySuccess should not be called after cancellation")
    }

    @Test
    fun executeQuery_whenAlreadyRunning_doesNotStartNewQuery() = runBlocking {
        val viewModel = QueryEditorViewModel(createTestRepository())
        val driver = FakeDriver(delayMs = 100)

        viewModel.executeQuery(
            sqlToRun = "SELECT * FROM users",
            driver = driver,
            profileId = "test-profile",
            profileName = "Test DB",
            onStatusChanged = {},
            onQuerySuccess = { _, _ -> },
            onQueryError = { _, _ -> },
        )

        delay(20) // Wait for first query to start
        assertTrue(viewModel.uiState.value.isRunning)

        // Try to execute second query while first is running
        viewModel.executeQuery(
            sqlToRun = "SELECT * FROM products",
            driver = driver,
            profileId = "test-profile",
            profileName = "Test DB",
            onStatusChanged = {},
            onQuerySuccess = { _, _ -> },
            onQueryError = { _, _ -> },
        )

        delay(150) // Wait for queries to finish

        // Only one query should have been executed
        assertEquals(1, driver.executeQueryCallCount, "Only first query should execute")
        assertTrue(driver.lastSql!!.contains("SELECT * FROM users"), "First query SQL should be executed (possibly wrapped)")
    }

    @Test
    fun executeInlineEdit_withoutTable_setsEditError() = runBlocking {
        val viewModel = QueryEditorViewModel(createTestRepository())
        val driver = FakeDriver()

        val result = viewModel.executeInlineEdit(
            driver = driver,
            lastExecutedSql = null,
            columns = listOf("id", "name"),
            columnName = "name",
            newValue = "updated",
            rowSnapshot = listOf("1", "test"),
            onStatusChanged = {},
        )

        assertTrue(result.isFailure)
        assertNotNull(viewModel.uiState.value.editError)
        assertTrue(viewModel.uiState.value.editError!!.contains("Inline edit requires last query"))
    }

    @Test
    fun executeInlineEdit_withoutIdColumn_setsEditError() = runBlocking {
        val viewModel = QueryEditorViewModel(createTestRepository())
        val driver = FakeDriver()

        val result = viewModel.executeInlineEdit(
            driver = driver,
            lastExecutedSql = "SELECT * FROM users",
            columns = listOf("username", "email"), // No 'id' column
            columnName = "username",
            newValue = "updated",
            rowSnapshot = listOf("john", "john@example.com"),
            onStatusChanged = {},
        )

        assertTrue(result.isFailure)
        assertNotNull(viewModel.uiState.value.editError)
        assertTrue(viewModel.uiState.value.editError!!.contains("requires an 'id' column"))
    }

    @Test
    fun executeInlineEdit_withEmptyId_setsEditError() = runBlocking {
        val viewModel = QueryEditorViewModel(createTestRepository())
        val driver = FakeDriver()

        val result = viewModel.executeInlineEdit(
            driver = driver,
            lastExecutedSql = "SELECT * FROM users",
            columns = listOf("id", "name"),
            columnName = "name",
            newValue = "updated",
            rowSnapshot = listOf("", "test"), // Empty id
            onStatusChanged = {},
        )

        assertTrue(result.isFailure)
        assertNotNull(viewModel.uiState.value.editError)
        assertTrue(viewModel.uiState.value.editError!!.contains("non-empty id value"))
    }
}
