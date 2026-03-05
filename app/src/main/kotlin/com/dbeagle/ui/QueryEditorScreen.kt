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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.error.ErrorHandler
import com.dbeagle.favorites.FileFavoritesRepository
import com.dbeagle.session.SessionViewModel
import com.dbeagle.viewmodel.QueryEditorViewModel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

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
    snackbarHostState: SnackbarHostState,
    sessionStates: Map<String, SessionViewModel.SessionUiState>,
) {
    val viewModel: QueryEditorViewModel = remember { GlobalContext.get().get() }
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    val sqlText = activeSession?.queryEditorSql ?: scratchSql
    val lastExecutedSql = activeSession?.lastExecutedSql
    val lastQueryResult = activeSession?.lastQueryResult
    val columns = activeSession?.resultColumns ?: emptyList()
    val rows = activeSession?.resultRows ?: emptyList()

    if (uiState.showExportDialog) {
        ExportDialog(
            onDismiss = { viewModel.hideExportDialog() },
            onExportRequested = { format, path, onProgress ->
                coroutineScope.launch {
                    viewModel.exportResults(
                        format = format,
                        path = path,
                        lastQueryResult = lastQueryResult,
                        onStatusChanged = onStatusTextChanged,
                        onProgress = onProgress,
                    )
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.editError != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearEditError() },
                title = { Text("Edit Error") },
                text = { Text(uiState.editError ?: "") },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearEditError() }) {
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
                viewModel.cancelQuery(onStatusTextChanged)
            },
            onRun = {
                if (uiState.isRunning) return@SQLEditor
                if (activeDriver == null) {
                    onStatusTextChanged("Status: No active connection")
                    return@SQLEditor
                }

                val pid = activeProfileId
                if (pid == null) {
                    onStatusTextChanged("Status: No active connection")
                    return@SQLEditor
                }

                sessionViewModel.clearQueryResult(pid)

                val sqlToRun = sessionStates[pid]?.queryEditorSql ?: ""

                viewModel.executeQuery(
                    sqlToRun = sqlToRun,
                    driver = activeDriver,
                    profileId = pid,
                    profileName = activeProfileName ?: "Connection",
                    onStatusChanged = onStatusTextChanged,
                    onQuerySuccess = { result, _ ->
                        sessionViewModel.recordQueryResult(pid, sqlToRun, result)
                    },
                    onQueryError = { message, exception ->
                        ErrorHandler.showQueryError(
                            snackbarHostState,
                            coroutineScope,
                            "Query error: $message",
                            exception,
                        )
                    },
                )
            },
            isRunning = uiState.isRunning,
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
                onClick = { viewModel.showExportDialog() },
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

                viewModel.executeInlineEdit(
                    driver = driver,
                    lastExecutedSql = lastExecutedSql,
                    columns = columns,
                    columnName = columnName,
                    newValue = newValue,
                    rowSnapshot = rowSnapshot,
                    onStatusChanged = onStatusTextChanged,
                )
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
