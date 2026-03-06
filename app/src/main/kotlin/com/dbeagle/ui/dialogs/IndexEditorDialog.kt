package com.dbeagle.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.ddl.DDLDialect
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.IndexDDLBuilder
import com.dbeagle.ddl.IndexDefinition
import com.dbeagle.ddl.ValidationResult
import com.dbeagle.viewmodel.IndexEditorViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class IndexType(val displayName: String) {
    REGULAR("Regular Index"),
    UNIQUE("Unique Index"),
    PRIMARY_KEY("Primary Key"),
    FOREIGN_KEY("Foreign Key"),
}

@Composable
fun IndexEditorDialog(
    dialect: DDLDialect,
    tables: List<String>,
    getColumnsForTable: suspend (tableName: String) -> List<String>,
    onDismiss: () -> Unit,
    onCreate: suspend (ddl: String) -> Result<Unit>,
) {
    val viewModel: IndexEditorViewModel = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var indexName by remember { mutableStateOf("") }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    val selectedColumns = remember { mutableStateListOf<String>() }
    var indexType by remember { mutableStateOf(IndexType.REGULAR) }
    var unique by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var isTableDropdownExpanded by remember { mutableStateOf(false) }
    var isColumnsDropdownExpanded by remember { mutableStateOf(false) }
    var isIndexTypeDropdownExpanded by remember { mutableStateOf(false) }

    // Sync indexType changes with unique flag for backward compatibility
    LaunchedEffect(indexType) {
        unique = when (indexType) {
            IndexType.UNIQUE, IndexType.PRIMARY_KEY -> true
            else -> false
        }
    }

    val nameValidation = if (indexName.isNotBlank()) {
        DDLValidator.validateIdentifier(indexName)
    } else {
        ValidationResult.Invalid(listOf("Index name is required"))
    }
    val isNameValid = nameValidation is ValidationResult.Valid

    val validationErrors = when (nameValidation) {
        is ValidationResult.Invalid -> nameValidation.errors
        ValidationResult.Valid -> emptyList()
    }

    val isFormValid = isNameValid && selectedTable != null && selectedColumns.isNotEmpty()

    val generatedDdl = if (isFormValid) {
        IndexDDLBuilder.buildCreateIndex(
            dialect = dialect,
            index = IndexDefinition(
                name = indexName,
                tableName = selectedTable!!,
                columns = selectedColumns.toList(),
                unique = unique,
            ),
        )
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Index") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = indexName,
                    onValueChange = { indexName = it },
                    label = { Text("Index Name") },
                    singleLine = true,
                    isError = !isNameValid && indexName.isNotBlank(),
                    supportingText = {
                        if (!isNameValid && validationErrors.isNotEmpty()) {
                            Text(validationErrors.first())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = indexType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Index Type") },
                        supportingText = {
                            when (indexType) {
                                IndexType.PRIMARY_KEY -> Text("Uniquely identifies each row. Typically one column.")
                                IndexType.UNIQUE -> Text("Ensures column values are unique across rows.")
                                IndexType.FOREIGN_KEY -> Text("References another table's primary key.")
                                IndexType.REGULAR -> Text("Improves query performance for selected columns.")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isIndexTypeDropdownExpanded = true },
                        enabled = false,
                    )
                    DropdownMenu(
                        expanded = isIndexTypeDropdownExpanded,
                        onDismissRequest = { isIndexTypeDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IndexType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    indexType = type
                                    isIndexTypeDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                // Table dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedTable ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Table") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTableDropdownExpanded = true },
                        enabled = false,
                    )
                    DropdownMenu(
                        expanded = isTableDropdownExpanded,
                        onDismissRequest = { isTableDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        tables.forEach { table ->
                            DropdownMenuItem(
                                text = { Text(table) },
                                onClick = {
                                    selectedTable = table
                                    selectedColumns.clear()
                                    viewModel.loadColumnsForTable(table, getColumnsForTable)
                                    isTableDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                // Columns multi-select dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedColumns.joinToString(", "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Columns") },
                        placeholder = { Text("Select columns...") },
                        isError = uiState.errorMessage != null,
                        supportingText = {
                            when {
                                uiState.errorMessage != null -> Text(uiState.errorMessage!!)
                                uiState.isLoadingColumns -> Text("Loading columns...")
                            }
                        },
                        trailingIcon = {
                            if (uiState.isLoadingColumns) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedTable != null && !uiState.isLoadingColumns) {
                                    isColumnsDropdownExpanded = true
                                }
                            },
                        enabled = selectedTable != null && !uiState.isLoadingColumns,
                    )
                    DropdownMenu(
                        expanded = isColumnsDropdownExpanded,
                        onDismissRequest = { isColumnsDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        uiState.availableColumns.forEach { column ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Checkbox(
                                            checked = column in selectedColumns,
                                            onCheckedChange = null,
                                        )
                                        Text(column)
                                    }
                                },
                                onClick = {
                                    if (column in selectedColumns) {
                                        selectedColumns.remove(column)
                                    } else {
                                        selectedColumns.add(column)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { showPreview = true },
                    enabled = isFormValid,
                ) {
                    Text("Preview")
                }
                Button(
                    onClick = {
                        scope.launch {
                            onCreate(generatedDdl)
                        }
                    },
                    enabled = isFormValid,
                ) {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showPreview) {
        DDLPreviewDialog(
            ddlSql = generatedDdl,
            isDestructive = false,
            onDismiss = { showPreview = false },
            onExecute = {
                scope.launch {
                    onCreate(generatedDdl)
                    showPreview = false
                }
            },
        )
    }
}
