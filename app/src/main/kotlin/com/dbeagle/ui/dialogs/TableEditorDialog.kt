package com.dbeagle.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.ddl.ColumnDefinition
import com.dbeagle.ddl.ColumnType
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.TableDefinition
import com.dbeagle.ddl.ValidationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableEditorDialog(
    existingTable: TableDefinition?,
    allTables: List<String>,
    onDismiss: () -> Unit,
    onSave: (TableDefinition) -> Unit,
) {
    var tableName by remember { mutableStateOf(existingTable?.name ?: "") }
    val columns = remember {
        mutableStateListOf<ColumnDefinition>().apply {
            if (existingTable != null) {
                addAll(existingTable.columns)
            }
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val isEditMode = existingTable != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Edit Table: ${existingTable.name}" else "Create Table") },
        text = {
            Column(
                modifier = Modifier
                    .width(800.dp)
                    .height(600.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Columns") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Constraints") },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Indexes") },
                    )
                }

                when (selectedTab) {
                    0 -> ColumnsTab(
                        tableName = tableName,
                        onTableNameChange = { tableName = it },
                        isEditMode = isEditMode,
                        columns = columns,
                        validationError = validationError,
                    )
                    1 -> Text("Constraints - TODO", modifier = Modifier.padding(16.dp))
                    2 -> Text("Indexes - TODO", modifier = Modifier.padding(16.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tableDefinition = TableDefinition(
                        name = tableName,
                        columns = columns.toList(),
                    )

                    when (val result = DDLValidator.validateTableDefinition(tableDefinition)) {
                        is ValidationResult.Invalid -> {
                            validationError = result.errors.joinToString("\n")
                        }
                        ValidationResult.Valid -> {
                            validationError = null
                            onSave(tableDefinition)
                        }
                    }
                },
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

@Composable
private fun ColumnsTab(
    tableName: String,
    onTableNameChange: (String) -> Unit,
    isEditMode: Boolean,
    columns: SnapshotStateList<ColumnDefinition>,
    validationError: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = tableName,
            onValueChange = onTableNameChange,
            label = { Text("Table Name") },
            singleLine = true,
            readOnly = isEditMode,
            modifier = Modifier.fillMaxWidth(),
        )

        validationError?.let { error ->
            Text(
                text = error,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Text(
            text = "Columns",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(columns) { index, column ->
                ColumnRow(
                    column = column,
                    onUpdate = { updatedColumn ->
                        columns[index] = updatedColumn
                    },
                    onDelete = {
                        columns.removeAt(index)
                    },
                )
            }
        }

        Button(
            onClick = {
                columns.add(
                    ColumnDefinition(
                        name = "",
                        type = ColumnType.TEXT,
                        nullable = true,
                        defaultValue = null,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add Column")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnRow(
    column: ColumnDefinition,
    onUpdate: (ColumnDefinition) -> Unit,
    onDelete: () -> Unit,
) {
    var columnName by remember { mutableStateOf(column.name) }
    var columnType by remember { mutableStateOf(column.type) }
    var nullable by remember { mutableStateOf(column.nullable) }
    var defaultValue by remember { mutableStateOf(column.defaultValue ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }

    fun notifyUpdate() {
        onUpdate(
            ColumnDefinition(
                name = columnName,
                type = columnType,
                nullable = nullable,
                defaultValue = defaultValue.takeIf { it.isNotBlank() },
            ),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = columnName,
            onValueChange = {
                columnName = it
                notifyUpdate()
            },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )

        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
            modifier = Modifier.weight(1.5f),
        ) {
            OutlinedTextField(
                value = columnType.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false },
            ) {
                ColumnType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.name) },
                        onClick = {
                            columnType = type
                            typeExpanded = false
                            notifyUpdate()
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = nullable,
                onCheckedChange = {
                    nullable = it
                    notifyUpdate()
                },
            )
            Text("Nullable")
        }

        OutlinedTextField(
            value = defaultValue,
            onValueChange = {
                defaultValue = it
                notifyUpdate()
            },
            label = { Text("Default") },
            singleLine = true,
            modifier = Modifier.weight(1.5f),
        )

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete column")
        }
    }
}
