package com.dbeagle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.driver.DatabaseCapability
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.navigation.NavigationTab
import com.dbeagle.session.SessionViewModel
import com.dbeagle.ui.dialogs.DDLPreviewDialog
import com.dbeagle.ui.dialogs.SequenceEditorDialog
import com.dbeagle.ui.dialogs.TableEditorDialog
import com.dbeagle.viewmodel.DDLExecutionException
import com.dbeagle.viewmodel.SchemaEditorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SchemaBrowserScreen(
    sessionViewModel: SessionViewModel,
    activeProfileId: String?,
    sessionStates: Map<String, SessionViewModel.SessionUiState>,
    activeDriver: DatabaseDriver?,
    activeProfileName: String?,
    onStatusTextChanged: (String) -> Unit,
    selectedTab: NavigationTab,
) {
    val coroutineScope = rememberCoroutineScope()
    var schemaJob by remember(activeProfileId) { mutableStateOf<Job?>(null) }

    var showTableEditor by remember { mutableStateOf(false) }
    var showSequenceEditor by remember { mutableStateOf(false) }
    var editingTable by remember { mutableStateOf<String?>(null) }
    var editingSequence by remember { mutableStateOf<String?>(null) }
    var showDDLPreview by remember { mutableStateOf(false) }
    var previewDDL by remember { mutableStateOf("") }
    var previewIsDestructive by remember { mutableStateOf(false) }
    var pendingDDLExecution by remember { mutableStateOf<suspend () -> Result<Unit>>({ Result.success(Unit) }) }
    var showDDLError by remember { mutableStateOf(false) }
    var ddlErrorMessage by remember { mutableStateOf("") }

    val ttlMs = 5 * 60 * 1000L
    val pid = activeProfileId
    val schemaState = pid?.let { sessionStates[it]?.schema } ?: SessionViewModel.SchemaUiState()
    val isLoadingSchema = schemaState.isLoading
    val schemaNodes = schemaState.nodes
    val schemaDialogError = schemaState.dialogError
    val columnsCache = schemaState.columnsCache
    val schemaMetadata = schemaState.schemaMetadata

    fun isExpired(loadedAt: Long?): Boolean {
        if (loadedAt == null) return true
        return (System.currentTimeMillis() - loadedAt) > ttlMs
    }

    fun buildTree(schema: SchemaMetadata): List<SchemaTreeNode> {
        val tables = schema.tables
            .sortedWith(compareBy({ it.schema }, { it.name }))
            .map { t ->
                val tableKey = "${t.schema}.${t.name}"
                val cached = columnsCache[tableKey]?.columns ?: emptyList()
                SchemaTreeNode.Table(
                    id = "table:$tableKey",
                    label = t.name,
                    children = cached,
                )
            }

        val views = schema.views
            .sorted()
            .map { v ->
                SchemaTreeNode.View(
                    id = "view:$v",
                    label = v,
                )
            }

        val indexes = schema.indexes
            .sorted()
            .map { idx ->
                SchemaTreeNode.Index(
                    id = "index:$idx",
                    label = idx,
                )
            }

        val sections = mutableListOf(
            SchemaTreeNode.Section(
                id = "section:tables",
                label = "Tables",
                children = tables,
            ),
            SchemaTreeNode.Section(
                id = "section:views",
                label = "Views",
                children = views,
            ),
            SchemaTreeNode.Section(
                id = "section:indexes",
                label = "Indexes",
                children = indexes,
            ),
        )

        // Add Sequences section only if database supports it
        if (activeDriver?.getCapabilities()?.contains(DatabaseCapability.Sequences) == true) {
            val sequences = schema.sequences
                .sortedBy { it.name }
                .map { seq ->
                    SchemaTreeNode.Sequence(
                        id = "sequence:${seq.name}",
                        label = seq.name,
                        increment = seq.increment,
                    )
                }
            sections.add(
                SchemaTreeNode.Section(
                    id = "section:sequences",
                    label = "Sequences",
                    children = sequences,
                ),
            )
        }

        return sections
    }

    fun updateTableChildren(
        tableKey: String,
        newChildren: List<SchemaTreeNode.Column>,
    ) {
        if (pid == null) return
        sessionViewModel.updateSchemaState(pid) { s ->
            s.copy(
                nodes = s.nodes.map { node ->
                    if (node is SchemaTreeNode.Section && node.id == "section:tables") {
                        SchemaTreeNode.Section(
                            id = node.id,
                            label = node.label,
                            children = node.children.map { child ->
                                if (
                                    child is SchemaTreeNode.Table &&
                                    child.id == "table:$tableKey"
                                ) {
                                    SchemaTreeNode.Table(
                                        id = child.id,
                                        label = child.label,
                                        children = newChildren,
                                    )
                                } else {
                                    child
                                }
                            },
                        )
                    } else {
                        node
                    }
                },
            )
        }
    }

    fun forceRefresh() {
        if (pid == null) return
        sessionViewModel.updateSchemaState(pid) { s ->
            s.copy(
                loadedAtMs = null,
                columnsCache = emptyMap(),
                nodes = emptyList(),
                dialogError = null,
                isLoading = false,
            )
        }
    }

    fun ensureSchemaLoaded(force: Boolean) {
        if (activeDriver == null) {
            if (pid != null) {
                sessionViewModel.updateSchemaState(pid) { s ->
                    s.copy(nodes = emptyList(), loadedAtMs = null, columnsCache = emptyMap())
                }
            }
            return
        }

        if (pid == null) return
        val current = sessionStates[pid]?.schema ?: return
        if (!force && !isExpired(current.loadedAtMs) && current.nodes.isNotEmpty()) return
        if (current.isLoading) return

        schemaJob?.cancel()
        schemaJob = coroutineScope.launch {
            val name = activeProfileName ?: "Connection"
            onStatusTextChanged("Status: Loading schema ($name)")
            sessionViewModel.updateSchemaState(pid) { it.copy(isLoading = true, dialogError = null) }
            try {
                val schema = withContext(Dispatchers.IO) { activeDriver.getSchema() }
                val nodes = buildTree(schema)
                val now = System.currentTimeMillis()
                sessionViewModel.updateSchemaState(pid) {
                    it.copy(nodes = nodes, loadedAtMs = now, isLoading = false, schemaMetadata = schema)
                }
                onStatusTextChanged("Status: Schema loaded ($name)")
            } catch (_: CancellationException) {
                onStatusTextChanged("Status: Schema load canceled")
            } catch (e: Exception) {
                onStatusTextChanged("Status: Failed to load schema: ${e.message ?: "Error"}")
                sessionViewModel.updateSchemaState(pid) {
                    it.copy(isLoading = false, dialogError = e.message ?: "Failed to load schema")
                }
            } finally {
                sessionViewModel.updateSchemaState(pid) { it.copy(isLoading = false) }
            }
        }
    }

    LaunchedEffect(selectedTab, activeProfileId) {
        if (selectedTab != NavigationTab.SchemaBrowser) return@LaunchedEffect
        ensureSchemaLoaded(force = false)
    }

    if (schemaDialogError != null) {
        AlertDialog(
            onDismissRequest = {
                if (pid != null) sessionViewModel.updateSchemaState(pid) { it.copy(dialogError = null) }
            },
            title = { Text("Schema Error") },
            text = { Text(schemaDialogError) },
            confirmButton = {
                TextButton(onClick = {
                    if (pid != null) sessionViewModel.updateSchemaState(pid) { it.copy(dialogError = null) }
                }) {
                    Text("OK")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            val hasConnection = activeDriver != null
            if (isLoadingSchema) {
                Button(
                    onClick = { schemaJob?.cancel() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = {
                        if (!hasConnection) return@Button
                        forceRefresh()
                        ensureSchemaLoaded(force = true)
                    },
                    enabled = hasConnection,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Refresh", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (activeDriver == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No active connection. Connect to browse schema.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (isLoadingSchema && schemaNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SchemaTree(
                nodes = schemaNodes,
                modifier = Modifier.fillMaxSize(),
                onNodeExpansionChanged = { node, expanded ->
                    if (!expanded) return@SchemaTree
                    if (node !is SchemaTreeNode.Table) return@SchemaTree

                    val driver = activeDriver

                    val activePid = pid ?: return@SchemaTree

                    val tableKey = node.id.removePrefix("table:")
                    val tableName = node.label
                    val cached = columnsCache[tableKey]
                    if (cached != null && !isExpired(cached.loadedAtMs)) {
                        updateTableChildren(tableKey, cached.columns)
                        return@SchemaTree
                    }

                    coroutineScope.launch {
                        val name = activeProfileName ?: "Connection"
                        onStatusTextChanged("Status: Loading columns ($name: $tableName)")
                        try {
                            val fkMap = schemaMetadata?.foreignKeys
                                ?.filter { it.fromTable == tableName }
                                ?.associateBy { it.fromColumn }
                                ?: emptyMap()
                            
                            val cols = withContext(Dispatchers.IO) { driver.getColumns(tableName) }
                                .sortedBy { it.name }
                                .map { c ->
                                    val fk = fkMap[c.name]
                                    val fkTarget = fk?.let { "${it.toTable}.${it.toColumn}" }
                                    SchemaTreeNode.Column(
                                        id = "col:$tableKey.${c.name}",
                                        label = c.name,
                                        type = c.type,
                                        foreignKeyTarget = fkTarget,
                                    )
                                }
                            val now = System.currentTimeMillis()
                            sessionViewModel.updateSchemaState(activePid) { s ->
                                s.copy(
                                    columnsCache = s.columnsCache + (tableKey to SessionViewModel.ColumnCacheEntry(now, cols)),
                                )
                            }
                            updateTableChildren(tableKey, cols)
                            onStatusTextChanged("Status: Columns loaded ($name: $tableName)")
                        } catch (_: CancellationException) {
                            onStatusTextChanged("Status: Query canceled")
                        } catch (e: Exception) {
                            onStatusTextChanged("Status: Failed to load columns: ${e.message ?: "Error"}")
                            sessionViewModel.updateSchemaState(activePid) {
                                it.copy(dialogError = e.message ?: "Failed to load columns")
                            }
                        }
                    }
                },
                onCopyName = { name -> println("App: Copy Name -> $name") },
                onViewData = { name -> println("App: View Data -> $name") },
                onNewTable = {
                    editingTable = null
                    showTableEditor = true
                },
                onEditTable = { tableName ->
                    editingTable = tableName
                    showTableEditor = true
                },
                onDropTable = { tableName ->
                    val driver = activeDriver ?: return@SchemaTree
                    coroutineScope.launch {
                        val ddlResult = SchemaEditorViewModel.dropTableDDL(driver, tableName, cascade = true)
                        ddlResult.onSuccess { ddl ->
                            previewDDL = ddl
                            previewIsDestructive = true
                            pendingDDLExecution = {
                                SchemaEditorViewModel.executeTableDrop(driver, ddl)
                            }
                            showDDLPreview = true
                        }.onFailure { error ->
                            ddlErrorMessage = error.message ?: "Failed to generate DROP DDL"
                            showDDLError = true
                        }
                    }
                },
                onNewSequence = {
                    editingSequence = null
                    showSequenceEditor = true
                },
                onEditSequence = { sequenceName ->
                    editingSequence = sequenceName
                    showSequenceEditor = true
                },
                onDropSequence = { sequenceName ->
                    val driver = activeDriver ?: return@SchemaTree
                    coroutineScope.launch {
                        val ddlResult = SchemaEditorViewModel.dropSequenceDDL(driver, sequenceName, ifExists = true)
                        ddlResult.onSuccess { ddl ->
                            previewDDL = ddl
                            previewIsDestructive = true
                            pendingDDLExecution = {
                                SchemaEditorViewModel.executeSequenceDrop(driver, ddl)
                            }
                            showDDLPreview = true
                        }.onFailure { error ->
                            ddlErrorMessage = error.message ?: "Failed to generate DROP DDL"
                            showDDLError = true
                        }
                    }
                },
            )
        }

        if (showTableEditor) {
            val allTables = schemaMetadata?.tables?.map { it.name } ?: emptyList()
            
            // Load existing indexes for the table being edited
            val existingIndexes = editingTable?.let { tableName ->
                schemaMetadata?.indexDetails
                    ?.filter { it.tableName == tableName }
                    ?.map { idxMeta ->
                        com.dbeagle.ddl.IndexDefinition(
                            name = idxMeta.name,
                            tableName = idxMeta.tableName,
                            columns = idxMeta.columns,
                            unique = idxMeta.unique
                        )
                    } ?: emptyList()
            } ?: emptyList()
            
            val existingTableDef = editingTable?.let { tableName ->
                schemaMetadata?.tables?.find { it.name == tableName }?.let { tableMetadata ->
                    val tableKey = "${tableMetadata.schema}.${tableMetadata.name}"
                    val cachedColumns = columnsCache[tableKey]?.columns ?: emptyList()
                    
                    val columnDefs = cachedColumns.map { col ->
                        val colType = try {
                            com.dbeagle.ddl.ColumnType.valueOf(col.type.uppercase())
                        } catch (e: IllegalArgumentException) {
                            // Fallback to TEXT if type is unrecognized
                            com.dbeagle.ddl.ColumnType.TEXT
                        }
                        com.dbeagle.ddl.ColumnDefinition(
                            name = col.label,
                            type = colType,
                            nullable = true, // SchemaTreeNode.Column lacks nullable field
                            defaultValue = null
                        )
                    }
                    
                    val foreignKeyDefs = schemaMetadata?.foreignKeys
                        ?.filter { it.fromTable == tableName }
                        ?.map { fk ->
                            com.dbeagle.ddl.ForeignKeyDefinition(
                                name = null, // ForeignKeyRelationship lacks constraint name
                                columns = listOf(fk.fromColumn),
                                refTable = fk.toTable,
                                refColumns = listOf(fk.toColumn),
                                onDelete = null,
                                onUpdate = null
                            )
                        } ?: emptyList()
                    
                    com.dbeagle.ddl.TableDefinition(
                        name = tableMetadata.name,
                        columns = columnDefs,
                        primaryKey = tableMetadata.primaryKey.takeIf { it.isNotEmpty() },
                        foreignKeys = foreignKeyDefs,
                        uniqueConstraints = emptyList() // Metadata doesn't include unique constraints
                    )
                }
            }
            
            TableEditorDialog(
                existingTable = existingTableDef,
                existingIndexes = existingIndexes,
                allTables = allTables,
                onDismiss = { 
                    showTableEditor = false
                    editingTable = null
                },
                onSave = { tableDef, newIndexes ->
                    val driver = activeDriver
                    if (driver == null) {
                        showTableEditor = false
                        editingTable = null
                        return@TableEditorDialog
                    }
                    
                    coroutineScope.launch {
                        try {
                            val isCreateMode = editingTable == null
                            val ddlResult = if (isCreateMode) {
                                SchemaEditorViewModel.createTableDDL(driver, tableDef)
                            } else {
                                val oldTableDef = existingTableDef ?: return@launch
                                
                                // Compute column changes
                                val addedColumns = tableDef.columns.filter { newCol ->
                                    oldTableDef.columns.none { it.name == newCol.name }
                                }
                                val droppedColumns = oldTableDef.columns.filter { oldCol ->
                                    tableDef.columns.none { it.name == oldCol.name }
                                }.map { it.name }
                                
                                // Compute constraint changes
                                val addedConstraints = buildList<com.dbeagle.ddl.ConstraintDefinition> {
                                    // PK changes: add if new PK exists and differs from old
                                    if (tableDef.primaryKey != null && tableDef.primaryKey != oldTableDef.primaryKey) {
                                        add(com.dbeagle.ddl.ConstraintDefinition.PrimaryKey(tableDef.primaryKey!!))
                                    }
                                    
                                    // New FKs
                                    tableDef.foreignKeys.forEach { newFk ->
                                        val exists = oldTableDef.foreignKeys.any { oldFk ->
                                            oldFk.columns == newFk.columns && 
                                            oldFk.refTable == newFk.refTable && 
                                            oldFk.refColumns == newFk.refColumns
                                        }
                                        if (!exists) {
                                            add(com.dbeagle.ddl.ConstraintDefinition.ForeignKey(newFk))
                                        }
                                    }
                                    
                                    // New unique constraints
                                    tableDef.uniqueConstraints.forEach { newUnique ->
                                        val exists = oldTableDef.uniqueConstraints.any { it == newUnique }
                                        if (!exists) {
                                            add(com.dbeagle.ddl.ConstraintDefinition.Unique(null, newUnique))
                                        }
                                    }
                                }
                                
                                val droppedConstraints = buildList<String> {
                                    // PK drop: if old PK existed but new doesn't or differs
                                    if (oldTableDef.primaryKey != null && 
                                        (tableDef.primaryKey == null || tableDef.primaryKey != oldTableDef.primaryKey)) {
                                        // Convention: {table}_pkey for PK constraint name
                                        add("${editingTable}_pkey")
                                    }
                                    
                                    // Dropped FKs - Note: requires constraint names which may not be available
                                    // This is a limitation documented in the notepad
                                    oldTableDef.foreignKeys.forEach { oldFk ->
                                        val stillExists = tableDef.foreignKeys.any { newFk ->
                                            newFk.columns == oldFk.columns && 
                                            newFk.refTable == oldFk.refTable && 
                                            newFk.refColumns == oldFk.refColumns
                                        }
                                        if (!stillExists && oldFk.name != null) {
                                            add(oldFk.name!!)
                                        }
                                    }
                                    
                                    // Dropped unique constraints - similar limitation
                                    oldTableDef.uniqueConstraints.forEachIndexed { idx, oldUnique ->
                                        val stillExists = tableDef.uniqueConstraints.any { it == oldUnique }
                                        if (!stillExists) {
                                            // Convention fallback if name not available
                                            add("${editingTable}_unique_$idx")
                                        }
                                    }
                                }
                                
                                // Compute index changes
                                val addedIndexes = newIndexes.filter { newIdx ->
                                    existingIndexes.none { it.name == newIdx.name }
                                }
                                val droppedIndexes = existingIndexes.filter { oldIdx ->
                                    newIndexes.none { it.name == oldIdx.name }
                                }.map { it.name }
                                
                                val changes = com.dbeagle.viewmodel.TableChanges(
                                    addedColumns = addedColumns,
                                    droppedColumns = droppedColumns,
                                    addedConstraints = addedConstraints,
                                    droppedConstraints = droppedConstraints,
                                    addedIndexes = addedIndexes,
                                    droppedIndexes = droppedIndexes
                                )
                                SchemaEditorViewModel.alterTableDDL(driver, editingTable!!, changes)
                            }
                            
                            ddlResult.onSuccess { ddl ->
                                previewDDL = ddl
                                previewIsDestructive = false
                                pendingDDLExecution = if (isCreateMode) {
                                    { SchemaEditorViewModel.executeTableCreate(driver, ddl) }
                                } else {
                                    { SchemaEditorViewModel.executeTableAlter(driver, ddl) }
                                }
                                showDDLPreview = true
                                showTableEditor = false
                            }.onFailure { error ->
                                ddlErrorMessage = error.message ?: "Failed to generate DDL"
                                showDDLError = true
                            }
                        } catch (e: Exception) {
                            ddlErrorMessage = e.message ?: "Unknown error"
                            showDDLError = true
                        }
                    }
                },
            )
        }

        if (showSequenceEditor) {
            val existingSeq = editingSequence?.let { seqName ->
                schemaMetadata?.sequences?.find { it.name == seqName }
            }
            
            SequenceEditorDialog(
                existingSequence = existingSeq,
                onDismiss = {
                    showSequenceEditor = false
                    editingSequence = null
                },
                onSave = { seqMetadata ->
                    val driver = activeDriver
                    if (driver == null) {
                        showSequenceEditor = false
                        editingSequence = null
                        return@SequenceEditorDialog
                    }
                    
                    coroutineScope.launch {
                        try {
                            val isCreateMode = editingSequence == null
                            val ddlResult = if (isCreateMode) {
                                SchemaEditorViewModel.createSequenceDDL(driver, seqMetadata)
                            } else {
                                val oldSeq = existingSeq ?: return@launch
                                val changes = com.dbeagle.ddl.SequenceChanges(
                                    increment = if (seqMetadata.increment != oldSeq.increment) seqMetadata.increment else null,
                                    minValue = if (seqMetadata.minValue != oldSeq.minValue) seqMetadata.minValue else null,
                                    maxValue = if (seqMetadata.maxValue != oldSeq.maxValue) seqMetadata.maxValue else null,
                                    restart = null
                                )
                                SchemaEditorViewModel.alterSequenceDDL(driver, editingSequence!!, changes)
                            }
                            
                            ddlResult.onSuccess { ddl ->
                                previewDDL = ddl
                                previewIsDestructive = false
                                pendingDDLExecution = if (isCreateMode) {
                                    { SchemaEditorViewModel.executeSequenceCreate(driver, ddl) }
                                } else {
                                    { SchemaEditorViewModel.executeSequenceAlter(driver, ddl) }
                                }
                                showDDLPreview = true
                                showSequenceEditor = false
                            }.onFailure { error ->
                                ddlErrorMessage = error.message ?: "Failed to generate DDL"
                                showDDLError = true
                            }
                        } catch (e: Exception) {
                            ddlErrorMessage = e.message ?: "Unknown error"
                            showDDLError = true
                        }
                    }
                },
            )
        }

        if (showDDLPreview) {
            DDLPreviewDialog(
                ddlSql = previewDDL,
                isDestructive = previewIsDestructive,
                onDismiss = {
                    showDDLPreview = false
                },
                onExecute = {
                    coroutineScope.launch {
                        val name = activeProfileName ?: "Connection"
                        onStatusTextChanged("Status: Executing DDL ($name)")
                        try {
                            val result = pendingDDLExecution()
                            result.onSuccess {
                                onStatusTextChanged("Status: DDL executed successfully ($name)")
                                showDDLPreview = false
                                editingSequence = null
                                editingTable = null
                                forceRefresh()
                                ensureSchemaLoaded(force = true)
                            }.onFailure { error ->
                                onStatusTextChanged("Status: DDL execution failed")
                                showDDLPreview = false
                                ddlErrorMessage = if (error is DDLExecutionException) {
                                    "${error.userError.title}: ${error.userError.description}\n${error.userError.suggestion}"
                                } else {
                                    error.message ?: "Unknown error"
                                }
                                showDDLError = true
                            }
                        } catch (e: Exception) {
                            onStatusTextChanged("Status: DDL execution failed")
                            showDDLPreview = false
                            ddlErrorMessage = e.message ?: "Unknown error"
                            showDDLError = true
                        }
                    }
                },
            )
        }
        
        if (showDDLError) {
            AlertDialog(
                onDismissRequest = {
                    showDDLError = false
                    ddlErrorMessage = ""
                },
                title = { Text("DDL Error") },
                text = { Text(ddlErrorMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showDDLError = false
                        ddlErrorMessage = ""
                    }) {
                        Text("OK")
                    }
                },
            )
        }
    }
}
