package com.dbeagle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.session.SessionViewModel
import com.dbeagle.viewmodel.TableDataEditorViewModel
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Save
import compose.icons.fontawesomeicons.solid.Sync
import compose.icons.fontawesomeicons.solid.Trash
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TableDataEditorScreen(
    sessionViewModel: SessionViewModel,
    connectionId: String,
    tableName: String,
    onStatusTextChanged: (String) -> Unit,
    onCloseRequested: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val viewModel: TableDataEditorViewModel = koinInject()
    val driver = sessionViewModel.getDriver(connectionId)
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showUnsavedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(driver, tableName) {
        if (driver != null) {
            viewModel.loadTableData(driver, tableName)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (uiState.hasPendingChanges) {
                showUnsavedDialog = true
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardChanges()
                    showUnsavedDialog = false
                    onCloseRequested()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    viewModel.addNewRow()
                    onStatusTextChanged("New row added")
                },
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Plus,
                    contentDescription = "New Row",
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = {
                    onStatusTextChanged("Select a row to delete")
                },
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Trash,
                    contentDescription = "Delete Selected",
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = {
                    if (driver != null) {
                        coroutineScope.launch {
                            val result = viewModel.commitChanges(driver, tableName)
                            result.onSuccess { message ->
                                onStatusTextChanged(message)
                                snackbarHostState.showSnackbar(message)
                                viewModel.refresh(driver, tableName)
                            }.onFailure { error ->
                                val errorMsg = "Commit failed: ${error.message}"
                                onStatusTextChanged(errorMsg)
                                snackbarHostState.showSnackbar(errorMsg)
                            }
                        }
                    }
                },
                enabled = uiState.hasPendingChanges,
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Save,
                    contentDescription = "Commit Changes",
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = {
                    if (driver != null) {
                        viewModel.refresh(driver, tableName)
                        onStatusTextChanged("Table data refreshed")
                    }
                },
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Sync,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = if (uiState.hasPendingChanges) "Unsaved changes" else "No changes",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.hasPendingChanges) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Page ${uiState.currentPage + 1} | Total: ${uiState.totalRows} rows",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp),
            )
        }

        if (driver != null && uiState.columns.isNotEmpty()) {
            ResultGrid(
                columns = uiState.columns,
                rows = uiState.rows,
                onCellCommit = { rowIndex, columnName, newValue, _ ->
                    viewModel.updateCell(rowIndex, columnName, newValue)
                    onStatusTextChanged("Cell updated (not committed)")
                    Result.success(Unit)
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = "No data to display",
                modifier = Modifier.padding(16.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (driver != null) {
                        viewModel.previousPage(driver, tableName)
                    }
                },
                enabled = uiState.currentPage > 0,
            ) {
                Text("Previous")
            }

            TextButton(
                onClick = {
                    if (driver != null) {
                        viewModel.nextPage(driver, tableName)
                    }
                },
                enabled = (uiState.currentPage + 1) * uiState.pageSize < uiState.totalRows,
            ) {
                Text("Next")
            }
        }
    }
}
