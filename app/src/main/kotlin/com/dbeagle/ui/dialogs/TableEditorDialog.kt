package com.dbeagle.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import com.dbeagle.ddl.ForeignKeyDefinition
import com.dbeagle.ddl.IndexDefinition
import com.dbeagle.ddl.TableDefinition
import com.dbeagle.ddl.ValidationResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TableEditorDialog(
    existingTable: TableDefinition?,
    existingIndexes: List<IndexDefinition> = emptyList(),
    allTables: List<String>,
    onDismiss: () -> Unit,
    onSave: (TableDefinition, List<IndexDefinition>) -> Unit,
) {
    var tableName by remember { mutableStateOf(existingTable?.name ?: "") }
    val columns = remember {
        mutableStateListOf<ColumnDefinition>().apply {
            if (existingTable != null) {
                addAll(existingTable.columns)
            }
        }
    }
    val primaryKeyColumns = remember {
        mutableStateListOf<String>().apply {
            existingTable?.primaryKey?.let { addAll(it) }
        }
    }
    val foreignKeys = remember {
        mutableStateListOf<ForeignKeyDefinition>().apply {
            if (existingTable != null) {
                addAll(existingTable.foreignKeys)
            }
        }
    }
    val uniqueConstraints = remember {
        mutableStateListOf<List<String>>().apply {
            if (existingTable != null) {
                addAll(existingTable.uniqueConstraints)
            }
        }
    }
    val indexes = remember { 
        mutableStateListOf<IndexDefinition>().apply {
            addAll(existingIndexes)
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
                    1 -> ConstraintsTab(
                        columns = columns,
                        allTables = allTables,
                        primaryKeyColumns = primaryKeyColumns,
                        foreignKeys = foreignKeys,
                        uniqueConstraints = uniqueConstraints,
                    )
                    2 -> IndexesTab(
                        tableName = tableName,
                        columns = columns,
                        indexes = indexes,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tableDefinition = TableDefinition(
                        name = tableName,
                        columns = columns.toList(),
                        primaryKey = primaryKeyColumns.takeIf { it.isNotEmpty() },
                        foreignKeys = foreignKeys.toList(),
                        uniqueConstraints = uniqueConstraints.toList(),
                    )

                    when (val result = DDLValidator.validateTableDefinition(tableDefinition)) {
                        is ValidationResult.Invalid -> {
                            validationError = result.errors.joinToString("\n")
                        }
                        ValidationResult.Valid -> {
                            validationError = null
                            onSave(tableDefinition, indexes.toList())
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConstraintsTab(
    columns: SnapshotStateList<ColumnDefinition>,
    allTables: List<String>,
    primaryKeyColumns: SnapshotStateList<String>,
    foreignKeys: SnapshotStateList<ForeignKeyDefinition>,
    uniqueConstraints: SnapshotStateList<List<String>>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Primary Key Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Primary Key",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                if (columns.isEmpty()) {
                    Text(
                        text = "Add columns first to define primary key",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        columns.forEach { column ->
                            FilterChip(
                                selected = column.name in primaryKeyColumns,
                                onClick = {
                                    if (column.name in primaryKeyColumns) {
                                        primaryKeyColumns.remove(column.name)
                                    } else {
                                        primaryKeyColumns.add(column.name)
                                    }
                                },
                                label = { Text(column.name) },
                            )
                        }
                    }
                }
            }
        }

        // Foreign Keys Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Foreign Keys",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                if (foreignKeys.isEmpty()) {
                    Text(
                        text = "No foreign keys defined",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(foreignKeys) { index, fk ->
            ForeignKeyRow(
                fk = fk,
                columns = columns,
                allTables = allTables,
                onUpdate = { updatedFk ->
                    foreignKeys[index] = updatedFk
                },
                onDelete = {
                    foreignKeys.removeAt(index)
                },
            )
        }

        item {
            Button(
                onClick = {
                    foreignKeys.add(
                        ForeignKeyDefinition(
                            name = null,
                            columns = emptyList(),
                            refTable = "",
                            refColumns = emptyList(),
                            onDelete = null,
                            onUpdate = null,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add Foreign Key", modifier = Modifier.padding(start = 8.dp))
            }
        }

        // Unique Constraints Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Unique Constraints",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                if (uniqueConstraints.isEmpty()) {
                    Text(
                        text = "No unique constraints defined",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uniqueConstraints.forEachIndexed { index, constraint ->
                        UniqueConstraintRow(
                            columns = columns,
                            selectedColumns = constraint,
                            onUpdate = { updated ->
                                uniqueConstraints[index] = updated
                            },
                            onDelete = {
                                uniqueConstraints.removeAt(index)
                            },
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    uniqueConstraints.add(emptyList())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add Unique Constraint", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ForeignKeyRow(
    fk: ForeignKeyDefinition,
    columns: SnapshotStateList<ColumnDefinition>,
    allTables: List<String>,
    onUpdate: (ForeignKeyDefinition) -> Unit,
    onDelete: () -> Unit,
) {
    var fkName by remember { mutableStateOf(fk.name ?: "") }
    var refTable by remember { mutableStateOf(fk.refTable) }
    var localColumns by remember { mutableStateOf(fk.columns.toSet()) }
    var refColumns by remember { mutableStateOf(fk.refColumns.joinToString(", ")) }
    var onDeleteAction by remember { mutableStateOf(fk.onDelete ?: "") }
    var onUpdateAction by remember { mutableStateOf(fk.onUpdate ?: "") }
    
    var refTableExpanded by remember { mutableStateOf(false) }
    var onDeleteExpanded by remember { mutableStateOf(false) }
    var onUpdateExpanded by remember { mutableStateOf(false) }

    val referentialActions = listOf("", "CASCADE", "SET NULL", "RESTRICT", "NO ACTION")

    fun notifyUpdate() {
        onUpdate(
            ForeignKeyDefinition(
                name = fkName.takeIf { it.isNotBlank() },
                columns = localColumns.toList(),
                refTable = refTable,
                refColumns = refColumns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                onDelete = onDeleteAction.takeIf { it.isNotBlank() },
                onUpdate = onUpdateAction.takeIf { it.isNotBlank() },
            ),
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = fkName,
                onValueChange = {
                    fkName = it
                    notifyUpdate()
                },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete foreign key")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = refTableExpanded,
                onExpandedChange = { refTableExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = refTable,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Table") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(refTableExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = refTableExpanded,
                    onDismissRequest = { refTableExpanded = false },
                ) {
                    allTables.forEach { table ->
                        DropdownMenuItem(
                            text = { Text(table) },
                            onClick = {
                                refTable = table
                                refTableExpanded = false
                                notifyUpdate()
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = refColumns,
                onValueChange = {
                    refColumns = it
                    notifyUpdate()
                },
                label = { Text("Target Columns (comma-separated)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Local Columns",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            if (columns.isEmpty()) {
                Text(
                    text = "No columns available",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    columns.forEach { column ->
                        FilterChip(
                            selected = column.name in localColumns,
                            onClick = {
                                localColumns = if (column.name in localColumns) {
                                    localColumns - column.name
                                } else {
                                    localColumns + column.name
                                }
                                notifyUpdate()
                            },
                            label = { Text(column.name) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = onDeleteExpanded,
                onExpandedChange = { onDeleteExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = if (onDeleteAction.isEmpty()) "None" else onDeleteAction,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("ON DELETE") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(onDeleteExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = onDeleteExpanded,
                    onDismissRequest = { onDeleteExpanded = false },
                ) {
                    referentialActions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(if (action.isEmpty()) "None" else action) },
                            onClick = {
                                onDeleteAction = action
                                onDeleteExpanded = false
                                notifyUpdate()
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = onUpdateExpanded,
                onExpandedChange = { onUpdateExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = if (onUpdateAction.isEmpty()) "None" else onUpdateAction,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("ON UPDATE") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(onUpdateExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = onUpdateExpanded,
                    onDismissRequest = { onUpdateExpanded = false },
                ) {
                    referentialActions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(if (action.isEmpty()) "None" else action) },
                            onClick = {
                                onUpdateAction = action
                                onUpdateExpanded = false
                                notifyUpdate()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UniqueConstraintRow(
    columns: SnapshotStateList<ColumnDefinition>,
    selectedColumns: List<String>,
    onUpdate: (List<String>) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (columns.isEmpty()) {
            Text(
                text = "No columns available",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                columns.forEach { column ->
                    FilterChip(
                        selected = column.name in selectedColumns,
                        onClick = {
                            val updated = if (column.name in selectedColumns) {
                                selectedColumns - column.name
                            } else {
                                selectedColumns + column.name
                            }
                            onUpdate(updated)
                        },
                        label = { Text(column.name) },
                    )
                }
            }
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete unique constraint")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndexesTab(
    tableName: String,
    columns: SnapshotStateList<ColumnDefinition>,
    indexes: SnapshotStateList<IndexDefinition>,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Indexes",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(indexes) { index, indexDef ->
                IndexRow(
                    tableName = tableName,
                    index = indexDef,
                    columns = columns,
                    onUpdate = { updatedIndex ->
                        indexes[index] = updatedIndex
                    },
                    onDelete = {
                        indexes.removeAt(index)
                    },
                )
            }
        }

        Button(
            onClick = {
                indexes.add(
                    IndexDefinition(
                        name = "",
                        tableName = tableName,
                        columns = emptyList(),
                        unique = false,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add Index", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndexRow(
    tableName: String,
    index: IndexDefinition,
    columns: List<ColumnDefinition>,
    onUpdate: (IndexDefinition) -> Unit,
    onDelete: () -> Unit,
) {
    var indexName by remember { mutableStateOf(index.name) }
    var unique by remember { mutableStateOf(index.unique) }
    var selectedColumns by remember { mutableStateOf(index.columns.toSet()) }

    fun notifyUpdate() {
        onUpdate(
            IndexDefinition(
                name = indexName,
                tableName = tableName,
                columns = selectedColumns.toList(),
                unique = unique,
            ),
        )
    }

    fun generateIndexName() {
        if (selectedColumns.isNotEmpty()) {
            indexName = "idx_${tableName}_${selectedColumns.joinToString("_")}"
            notifyUpdate()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = indexName,
                onValueChange = {
                    indexName = it
                    notifyUpdate()
                },
                label = { Text("Index Name") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = unique,
                    onCheckedChange = {
                        unique = it
                        notifyUpdate()
                    },
                )
                Text("Unique")
            }

            IconButton(onClick = { generateIndexName() }) {
                Icon(Icons.Default.Add, contentDescription = "Generate index name")
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete index")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Columns",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            if (columns.isEmpty()) {
                Text(
                    text = "No columns available",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    columns.forEach { column ->
                        FilterChip(
                            selected = column.name in selectedColumns,
                            onClick = {
                                selectedColumns = if (column.name in selectedColumns) {
                                    selectedColumns - column.name
                                } else {
                                    selectedColumns + column.name
                                }
                                notifyUpdate()
                            },
                            label = { Text(column.name) },
                        )
                    }
                }
            }
        }
    }
}
