package com.dbeagle.viewmodel

import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.edit.InlineUpdate
import com.dbeagle.export.CsvExporter
import com.dbeagle.export.JsonExporter
import com.dbeagle.export.SqlExporter
import com.dbeagle.history.FileQueryHistoryRepository
import com.dbeagle.model.QueryHistoryEntry
import com.dbeagle.model.QueryResult
import com.dbeagle.query.QueryExecutor
import com.dbeagle.ui.ExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for Query Editor screen.
 * Manages query execution, export logic, history recording, and inline edits.
 */
class QueryEditorViewModel(
    private val historyRepository: FileQueryHistoryRepository,
) : BaseViewModel() {

    data class QueryEditorUiState(
        val isRunning: Boolean = false,
        val editError: String? = null,
        val showExportDialog: Boolean = false,
    )

    private val _uiState = MutableStateFlow(QueryEditorUiState())
    val uiState: StateFlow<QueryEditorUiState> = _uiState.asStateFlow()

    private var currentQueryJob: Job? = null

    /**
     * Cancels the currently running query.
     */
    fun cancelQuery(onStatusChanged: (String) -> Unit) {
        currentQueryJob?.cancel()
        updateStateFlow(_uiState) { it.copy(isRunning = false) }
        onStatusChanged("Status: Query canceled")
    }

    /**
     * Executes a SQL query.
     *
     * @param sqlToRun The SQL query to execute
     * @param driver The database driver to use
     * @param profileId The connection profile ID for history recording
     * @param profileName The connection profile name for status messages
     * @param onStatusChanged Callback for status text updates
     * @param onQuerySuccess Callback when query executes successfully
     * @param onQueryError Callback when query fails (message, exception?)
     */
    fun executeQuery(
        sqlToRun: String,
        driver: DatabaseDriver,
        profileId: String,
        profileName: String,
        onStatusChanged: (String) -> Unit,
        onQuerySuccess: (QueryResult.Success, Long) -> Unit,
        onQueryError: (String, Exception?) -> Unit,
    ) {
        if (_uiState.value.isRunning) return

        currentQueryJob = viewModelScope.launch {
            updateStateFlow(_uiState) { it.copy(isRunning = true) }
            onStatusChanged("Status: Running query ($profileName)")

            val startNs = System.nanoTime()
            try {
                val r = withContext(Dispatchers.IO) { QueryExecutor(driver).execute(sqlToRun) }
                when (r) {
                    is QueryResult.Success -> {
                        val durationMs = (System.nanoTime() - startNs) / 1_000_000
                        onStatusChanged("Status: ${r.rows.size} row(s) in ${durationMs}ms")

                        historyRepository.add(
                            QueryHistoryEntry(
                                query = sqlToRun,
                                durationMs = durationMs,
                                connectionProfileId = profileId,
                            ),
                        )

                        onQuerySuccess(r, durationMs)
                    }
                    is QueryResult.Error -> {
                        val durationMs = (System.nanoTime() - startNs) / 1_000_000
                        onStatusChanged("Status: Error in ${durationMs}ms: ${r.message}")

                        historyRepository.add(
                            QueryHistoryEntry(
                                query = sqlToRun,
                                durationMs = durationMs,
                                connectionProfileId = profileId,
                            ),
                        )

                        onQueryError(r.message, null)
                    }
                }
            } catch (_: CancellationException) {
                onStatusChanged("Status: Query canceled")
            } catch (e: Exception) {
                val durationMs = (System.nanoTime() - startNs) / 1_000_000
                onStatusChanged("Status: Error in ${durationMs}ms: ${e.message ?: "Error"}")
                onQueryError(e.message ?: "Unknown error", e)
            } finally {
                updateStateFlow(_uiState) { it.copy(isRunning = false) }
            }
        }
    }

    /**
     * Exports query results to a file.
     *
     * @param format The export format (CSV, JSON, SQL)
     * @param path The file path to export to
     * @param lastQueryResult The query result to export
     * @param onStatusChanged Callback for status text updates
     * @param onProgress Callback for export progress updates
     */
    suspend fun exportResults(
        format: ExportFormat,
        path: String,
        lastQueryResult: QueryResult.Success?,
        onStatusChanged: (String) -> Unit,
        onProgress: (Int, Boolean) -> Unit,
    ) {
        if (lastQueryResult == null) {
            onStatusChanged("Status: No query result to export")
            return
        }

        try {
            val outputFile = File(path)
            val exporter = when (format) {
                ExportFormat.CSV -> CsvExporter()
                ExportFormat.JSON -> JsonExporter()
                ExportFormat.SQL -> SqlExporter()
            }
            exporter.export(
                outputFile,
                lastQueryResult,
                lastQueryResult.resultSet,
            ) { rowCount, isDone ->
                onProgress(rowCount, isDone)
                if (isDone) {
                    onStatusChanged("Status: Exported $rowCount rows to $path")
                }
            }
        } catch (e: Exception) {
            onStatusChanged("Status: Export failed: ${e.message}")
        }
    }

    /**
     * Performs an inline edit (UPDATE) on a table cell.
     *
     * @param driver The database driver to use
     * @param lastExecutedSql The last executed SQL query (for table inference)
     * @param columns The result columns from the last query
     * @param columnName The column being edited
     * @param newValue The new value for the cell
     * @param rowSnapshot The entire row snapshot (for finding id)
     * @param onStatusChanged Callback for status text updates
     * @return Result indicating success or failure
     */
    suspend fun executeInlineEdit(
        driver: DatabaseDriver,
        lastExecutedSql: String?,
        columns: List<String>,
        columnName: String,
        newValue: Any,
        rowSnapshot: List<String>,
        onStatusChanged: (String) -> Unit,
    ): Result<Unit> {
        val table = lastExecutedSql?.let { InlineUpdate.inferTableNameFromSelectAll(it) }
        if (table.isNullOrBlank()) {
            val msg = "Inline edit requires last query like: SELECT * FROM <table>"
            updateStateFlow(_uiState) { it.copy(editError = msg) }
            return Result.failure(IllegalStateException(msg))
        }

        val idIndex = columns.indexOfFirst { it.equals("id", ignoreCase = true) }
        if (idIndex < 0) {
            val msg = "Inline edit requires an 'id' column in result set"
            updateStateFlow(_uiState) { it.copy(editError = msg) }
            return Result.failure(IllegalStateException(msg))
        }

        val idValue = rowSnapshot.getOrNull(idIndex)
        if (idValue.isNullOrBlank()) {
            val msg = "Inline edit requires a non-empty id value"
            updateStateFlow(_uiState) { it.copy(editError = msg) }
            return Result.failure(IllegalStateException(msg))
        }

        val stmt = InlineUpdate.buildUpdateById(
            table = table,
            column = columnName,
            value = newValue,
            id = idValue,
        )

        val r = try {
            withContext(Dispatchers.IO) { QueryExecutor(driver).execute(stmt.sql, stmt.params) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            updateStateFlow(_uiState) { it.copy(editError = msg) }
            onStatusChanged("Status: Update failed: $msg")
            return Result.failure(IllegalStateException(msg))
        }

        return when (r) {
            is QueryResult.Success -> {
                onStatusChanged("Status: Updated $table.$columnName for id=$idValue")
                Result.success(Unit)
            }
            is QueryResult.Error -> {
                val msg = r.message
                updateStateFlow(_uiState) { it.copy(editError = msg) }
                onStatusChanged("Status: Update failed: $msg")
                Result.failure(IllegalStateException(msg))
            }
        }
    }

    /**
     * Shows the export dialog.
     */
    fun showExportDialog() {
        updateStateFlow(_uiState) { it.copy(showExportDialog = true) }
    }

    /**
     * Hides the export dialog.
     */
    fun hideExportDialog() {
        updateStateFlow(_uiState) { it.copy(showExportDialog = false) }
    }

    /**
     * Clears the edit error.
     */
    fun clearEditError() {
        updateStateFlow(_uiState) { it.copy(editError = null) }
    }
}
