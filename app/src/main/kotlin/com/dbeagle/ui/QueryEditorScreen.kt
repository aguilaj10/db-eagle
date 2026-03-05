package com.dbeagle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.edit.InlineUpdate
import com.dbeagle.error.ErrorHandler
import com.dbeagle.export.CsvExporter
import com.dbeagle.export.JsonExporter
import com.dbeagle.export.SqlExporter
import com.dbeagle.favorites.FileFavoritesRepository
import com.dbeagle.history.FileQueryHistoryRepository
import com.dbeagle.model.QueryHistoryEntry
import com.dbeagle.model.QueryResult
import com.dbeagle.query.QueryExecutor
import com.dbeagle.session.SessionViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun QueryEditorScreen(
    sessionViewModel: SessionViewModel,
    activeProfileId: String?,
    activeSession: SessionViewModel.SessionUiState?,
    activeDriver: DatabaseDriver?,
    activeProfileName: String?,
    scratchSql: String,
    onScratchSqlChange: (String) -> Unit,
    onStatusTextChanged: (String) -> Unit,
    favoriteQueryDraft: String,
    showSaveFavoriteDialog: Boolean,
    onShowSaveFavoriteDialog: (Boolean) -> Unit,
    onFavoriteQueryDraftChange: (String) -> Unit,
    favoritesRepository: FileFavoritesRepository,
    historyRepository: FileQueryHistoryRepository,
    snackbarHostState: SnackbarHostState,
    sessionStates: Map<String, SessionViewModel.SessionUiState>,
) {
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember(activeProfileId) { mutableStateOf(false) }
    var queryJob by remember(activeProfileId) { mutableStateOf<Job?>(null) }
    var editError by remember(activeProfileId) { mutableStateOf<String?>(null) }
    var showExportDialog by remember(activeProfileId) { mutableStateOf(false) }

    val sqlText = activeSession?.queryEditorSql ?: scratchSql
    val lastExecutedSql = activeSession?.lastExecutedSql
    val lastQueryResult = activeSession?.lastQueryResult
    val columns = activeSession?.resultColumns ?: emptyList()
    val rows = activeSession?.resultRows ?: emptyList()

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExportRequested = { format, path, onProgress ->
                if (lastQueryResult == null) {
                    onStatusTextChanged("Status: No query result to export")
                    return@ExportDialog
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
                            onStatusTextChanged("Status: Exported $rowCount rows to $path")
                        }
                    }
                } catch (e: Exception) {
                    onStatusTextChanged("Status: Export failed: ${e.message}")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (editError != null) {
            AlertDialog(
                onDismissRequest = { editError = null },
                title = { Text("Edit Error") },
                text = { Text(editError ?: "") },
                confirmButton = {
                    TextButton(onClick = { editError = null }) {
                        Text("OK")
                    }
                },
            )
        }

        SQLEditor(
            sql = sqlText,
            onSqlChange = {
                val pid = activeProfileId
                if (pid == null) {
                    onScratchSqlChange(it)
                } else {
                    sessionViewModel.updateQueryEditorSql(pid, it)
                }
            },
            onCancel = {
                queryJob?.cancel()
                isRunning = false
                onStatusTextChanged("Status: Query canceled")
            },
            onRun = {
                if (isRunning) return@SQLEditor
                if (activeDriver == null) {
                    onStatusTextChanged("Status: No active connection")
                    return@SQLEditor
                }

                queryJob = coroutineScope.launch {
                    isRunning = true
                    val name = activeProfileName ?: "Connection"
                    onStatusTextChanged("Status: Running query ($name)")

                    val pid = activeProfileId
                    if (pid == null) {
                        onStatusTextChanged("Status: No active connection")
                        isRunning = false
                        return@launch
                    }

                    sessionViewModel.clearQueryResult(pid)

                    val sqlToRun = sessionStates[pid]?.queryEditorSql ?: ""

                    val startNs = System.nanoTime()
                    try {
                        val r = withContext(Dispatchers.IO) { QueryExecutor(activeDriver).execute(sqlToRun) }
                        when (r) {
                            is QueryResult.Success -> {
                                sessionViewModel.recordQueryResult(pid, sqlToRun, r)
                                val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                onStatusTextChanged("Status: ${r.rows.size} row(s) in ${durationMs}ms")

                                historyRepository.add(
                                    QueryHistoryEntry(
                                        query = sqlToRun,
                                        durationMs = durationMs,
                                        connectionProfileId = pid,
                                    ),
                                )
                            }
                            is QueryResult.Error -> {
                                val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                onStatusTextChanged("Status: Error in ${durationMs}ms: ${r.message}")
                                ErrorHandler.showQueryError(
                                    snackbarHostState,
                                    coroutineScope,
                                    "Query error: ${r.message}",
                                )

                                historyRepository.add(
                                    QueryHistoryEntry(
                                        query = sqlToRun,
                                        durationMs = durationMs,
                                        connectionProfileId = pid,
                                    ),
                                )
                            }
                        }
                    } catch (_: CancellationException) {
                        onStatusTextChanged("Status: Query canceled")
                    } catch (e: Exception) {
                        val durationMs = (System.nanoTime() - startNs) / 1_000_000
                        onStatusTextChanged("Status: Error in ${durationMs}ms: ${e.message ?: "Error"}")
                        ErrorHandler.showQueryError(
                            snackbarHostState,
                            coroutineScope,
                            "Query error: ${e.message ?: "Unknown error"}",
                            e,
                        )
                    } finally {
                        isRunning = false
                    }
                }
            },
            isRunning = isRunning,
            onClear = {
                val pid = activeProfileId
                if (pid == null) onScratchSqlChange("") else sessionViewModel.updateQueryEditorSql(pid, "")
            },
            onSaveToFavorites = {
                onFavoriteQueryDraftChange(sqlText)
                onShowSaveFavoriteDialog(true)
            },
            modifier = Modifier.weight(0.4f),
        )

        HorizontalDivider(thickness = 2.dp)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = { showExportDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Export Data", style = MaterialTheme.typography.labelMedium)
            }
        }

        ResultGrid(
            columns = columns,
            rows = rows,
            pageSize = 25,
            modifier = Modifier.weight(0.6f),
            onCellCommit = { _, columnName, newValue, rowSnapshot ->
                val driver = activeDriver
                if (driver == null) {
                    onStatusTextChanged("Status: No active connection")
                    return@ResultGrid Result.failure(IllegalStateException("No active connection"))
                }

                val lastSql = lastExecutedSql
                val table = lastSql?.let { InlineUpdate.inferTableNameFromSelectAll(it) }
                if (table.isNullOrBlank()) {
                    val msg = "Inline edit requires last query like: SELECT * FROM <table>"
                    editError = msg
                    return@ResultGrid Result.failure(IllegalStateException(msg))
                }

                val idIndex = columns.indexOfFirst { it.equals("id", ignoreCase = true) }
                if (idIndex < 0) {
                    val msg = "Inline edit requires an 'id' column in result set"
                    editError = msg
                    return@ResultGrid Result.failure(IllegalStateException(msg))
                }

                val idValue = rowSnapshot.getOrNull(idIndex)
                if (idValue.isNullOrBlank()) {
                    val msg = "Inline edit requires a non-empty id value"
                    editError = msg
                    return@ResultGrid Result.failure(IllegalStateException(msg))
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
                    editError = msg
                    onStatusTextChanged("Status: Update failed: $msg")
                    return@ResultGrid Result.failure(IllegalStateException(msg))
                }

                when (r) {
                    is QueryResult.Success -> {
                        onStatusTextChanged("Status: Updated $table.$columnName for id=$idValue")
                        Result.success(Unit)
                    }
                    is QueryResult.Error -> {
                        val msg = r.message
                        editError = msg
                        onStatusTextChanged("Status: Update failed: $msg")
                        Result.failure(IllegalStateException(msg))
                    }
                }
            },
        )
    }
}

@Composable
fun SaveFavoriteDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSave: (name: String, tags: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Favorites") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("sql, reports, etc.") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Query: ${initialQuery.take(100)}${if (initialQuery.length > 100) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    onSave(name, tags)
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
