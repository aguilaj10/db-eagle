package com.dbeagle.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.dbeagle.ui.components.ReadonlyDropdownField
import com.dbeagle.viewmodel.IndexEditorViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class IndexType(val displayName: String) {
    REGULAR("Regular Index"),
    UNIQUE("Unique Index"),
    PRIMARY_KEY("Primary Key"),
    FOREIGN_KEY("Foreign Key"),
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var isColumnsDropdownExpanded by remember { mutableStateOf(false) }

    // Sync indexType changes with unique flag for backward compatibility
    LaunchedEffect(indexType) {
        unique = when (indexType) {
            IndexType.UNIQUE, IndexType.PRIMARY_KEY -> true
            else -> false
        }
    }

    // Load columns when table selection changes
    LaunchedEffect(selectedTable) {
        if (selectedTable != null) {
            viewModel.loadColumnsForTable(selectedTable!!, getColumnsForTable)
            selectedColumns.clear()
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
        val (schema, table) = selectedTable!!.let {
            if (it.contains(".")) {
                val parts = it.split(".", limit = 2)
                parts[0] to parts[1]
            } else {
                null to it
            }
        }
        
        IndexDDLBuilder.buildCreateIndex(
            dialect = dialect,
            index = IndexDefinition(
                name = indexName,
                tableName = table,
                schema = schema,
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

                ReadonlyDropdownField(
                    label = "Index Type",
                    value = indexType,
                    options = IndexType.entries,
                    onSelect = { indexType = it },
                    valueText = { it.displayName },
                    modifier = Modifier.fillMaxWidth()
                )

                ReadonlyDropdownField(
                    label = "Table",
                    value = selectedTable ?: "",
                    options = tables,
                    onSelect = { selectedTable = it },
                    modifier = Modifier.fillMaxWidth()
                )

                // Columns multi-select dropdown (custom due to multi-select + checkboxes)
                ExposedDropdownMenuBox(
                    expanded = isColumnsDropdownExpanded,
                    onExpandedChange = { 
                        if (selectedTable != null && !uiState.isLoadingColumns) {
                            isColumnsDropdownExpanded = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(isColumnsDropdownExpanded)
                            }
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        enabled = selectedTable != null && !uiState.isLoadingColumns,
                    )
                    ExposedDropdownMenu(
                        expanded = isColumnsDropdownExpanded,
                        onDismissRequest = { isColumnsDropdownExpanded = false },
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
