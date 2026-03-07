package com.dbeagle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dbeagle.ddl.ConstraintDefinition
import com.dbeagle.ddl.DDLDialect
import com.dbeagle.ddl.IndexDDLBuilder
import com.dbeagle.ddl.PostgreSQLDDLDialect
import com.dbeagle.ddl.SQLiteDDLDialect
import com.dbeagle.ddl.ViewDDLBuilder
import com.dbeagle.driver.DataDrivers
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.query.QueryExecutor
import com.dbeagle.session.SessionViewModel
import com.dbeagle.ui.dialogs.DDLPreviewDialog
import com.dbeagle.ui.dialogs.IndexEditorDialog
import com.dbeagle.ui.dialogs.SequenceEditorDialog
import com.dbeagle.ui.dialogs.TableEditorDialog
import com.dbeagle.ui.dialogs.ViewEditorDialog
import com.dbeagle.viewmodel.ConnectionListViewModel
import com.dbeagle.viewmodel.SchemaEditorViewModel
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ChevronDown
import compose.icons.fontawesomeicons.solid.ChevronRight
import compose.icons.fontawesomeicons.solid.Sync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

private fun inferColumnType(typeString: String): com.dbeagle.ddl.ColumnType {
    val normalized = typeString.uppercase()
    return when {
        normalized.contains("INT") && normalized.contains("BIG") -> com.dbeagle.ddl.ColumnType.BIGINT
        normalized.contains("SMALL") && normalized.contains("INT") -> com.dbeagle.ddl.ColumnType.SMALLINT
        normalized.contains("INT") -> com.dbeagle.ddl.ColumnType.INTEGER
        normalized.contains("DECIMAL") || normalized.contains("NUMERIC") -> com.dbeagle.ddl.ColumnType.DECIMAL
        normalized.contains("REAL") -> com.dbeagle.ddl.ColumnType.REAL
        normalized.contains("DOUBLE") -> com.dbeagle.ddl.ColumnType.DOUBLE_PRECISION
        normalized.contains("BOOL") -> com.dbeagle.ddl.ColumnType.BOOLEAN
        normalized.contains("UUID") -> com.dbeagle.ddl.ColumnType.UUID
        normalized.contains("JSON") && normalized.contains("B") -> com.dbeagle.ddl.ColumnType.JSONB
        normalized.contains("JSON") -> com.dbeagle.ddl.ColumnType.JSON
        normalized.contains("DATE") && !normalized.contains("TIME") -> com.dbeagle.ddl.ColumnType.DATE
        normalized.contains("TIMESTAMP") || normalized.contains("DATETIME") -> com.dbeagle.ddl.ColumnType.TIMESTAMP
        normalized.contains("BLOB") || normalized.contains("BYTEA") || normalized.contains("BINARY") -> com.dbeagle.ddl.ColumnType.BLOB
        normalized.contains("CHAR") || normalized.contains("TEXT") || normalized.contains("STRING") -> com.dbeagle.ddl.ColumnType.TEXT
        else -> com.dbeagle.ddl.ColumnType.TEXT
    }
}

// Dialog state management
data class DialogState(
    val profileId: String,
    val showTableEditor: Boolean = false,
    val editingTable: String? = null,
    val showSequenceEditor: Boolean = false,
    val editingSequence: String? = null,
    val showViewEditor: Boolean = false,
    val showIndexEditor: Boolean = false,
    val showDDLPreview: Boolean = false,
    val ddlSql: String = "",
    val isDestructive: Boolean = false,
    val pendingExecution: (suspend () -> Result<Unit>)? = null,
    val showError: Boolean = false,
    val errorMessage: String = "",
)

@Composable
fun ConnectionPanel(
    masterPassword: String,
    sessionViewModel: SessionViewModel,
    isCollapsed: Boolean,
    onCollapseToggle: () -> Unit,
    onNewConnection: () -> Unit,
    onStatusTextChanged: (String) -> Unit,
    onOpenTableEditor: (connectionId: String, tableName: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val viewModel: ConnectionListViewModel = remember(masterPassword) {
        GlobalContext.get().get { parametersOf(masterPassword) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val connectedProfileIds by sessionViewModel.connectedProfileIds.collectAsState()
    val connectingProfileId by sessionViewModel.connectingProfileId.collectAsState()
    val sessionStates by sessionViewModel.sessionStates.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var expandedSchemas by remember { mutableStateOf(setOf<String>()) }
    var dialogStates by remember { mutableStateOf<Map<String, DialogState>>(emptyMap()) }

    fun getDialectForDriver(driver: DatabaseDriver): DDLDialect = when (driver.getName()) {
        "PostgreSQL" -> PostgreSQLDDLDialect
        "SQLite" -> SQLiteDDLDialect
        else -> PostgreSQLDDLDialect
    }

    suspend fun executeDDL(driver: DatabaseDriver, ddl: String): Result<Unit> = try {
        withContext(Dispatchers.IO) {
            QueryExecutor(driver).execute(ddl)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun buildTree(schema: SchemaMetadata): List<SchemaTreeNode> {
        val tables = schema.tables
            .sortedWith(compareBy({ it.schema }, { it.name }))
            .map { t ->
                SchemaTreeNode.Table(
                    id = "table:${t.schema}.${t.name}",
                    label = t.name,
                    children = emptyList(), // Columns loaded on expansion
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

        val sequences = schema.sequences
            .sortedBy { it.name }
            .map { seq ->
                SchemaTreeNode.Sequence(
                    id = "sequence:${seq.name}",
                    label = seq.name,
                    increment = seq.increment,
                )
            }

        return listOfNotNull(
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
            if (sequences.isNotEmpty()) {
                SchemaTreeNode.Section(
                    id = "section:sequences",
                    label = "Sequences",
                    children = sequences,
                )
            } else {
                null
            },
        )
    }

    fun loadSchema(profileId: String, profileName: String) {
        val driver = sessionViewModel.getDriver(profileId) ?: return
        val currentState = sessionStates[profileId]?.schema
        if (currentState?.isLoading == true) return

        coroutineScope.launch {
            onStatusTextChanged("Status: Loading schema ($profileName)")
            sessionViewModel.updateSchemaState(profileId) { it.copy(isLoading = true) }
            try {
                val queryExecutor = QueryExecutor(driver)
                val schema = withContext(Dispatchers.IO) { queryExecutor.getSchema() }
                val nodes = buildTree(schema)
                sessionViewModel.updateSchemaState(profileId) {
                    it.copy(
                        nodes = nodes,
                        isLoading = false,
                        schemaMetadata = schema,
                        loadedAtMs = System.currentTimeMillis(),
                    )
                }
                onStatusTextChanged("Status: Schema loaded ($profileName)")
            } catch (_: CancellationException) {
                onStatusTextChanged("Status: Schema load canceled")
                sessionViewModel.updateSchemaState(profileId) { it.copy(isLoading = false) }
            } catch (e: Exception) {
                onStatusTextChanged("Status: Failed to load schema: ${e.message ?: "Error"}")
                sessionViewModel.updateSchemaState(profileId) {
                    it.copy(isLoading = false, dialogError = e.message ?: "Failed to load schema")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        DataDrivers.registerAll()
        viewModel.refreshProfiles()
    }

    // Auto-load schema when connection is established
    LaunchedEffect(connectedProfileIds) {
        connectedProfileIds.forEach { profileId ->
            val profile = uiState.profiles.find { it.id == profileId }
            val schemaState = sessionStates[profileId]?.schema
            if (profile != null && schemaState?.schemaMetadata == null && schemaState?.isLoading == false) {
                loadSchema(profileId, profile.name)
            }
        }
    }

    Row(modifier = modifier.fillMaxHeight()) {
        IconButton(
            onClick = onCollapseToggle,
            modifier = Modifier.align(Alignment.Top).padding(top = 8.dp),
        ) {
            Icon(
                imageVector = if (isCollapsed) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                },
                contentDescription = if (isCollapsed) "Expand sidebar" else "Collapse sidebar",
            )
        }

        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            ) {
                Button(
                    onClick = onNewConnection,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Connection", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Connections",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(24.dp),
                            )
                        }
                        uiState.error != null -> {
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        uiState.profiles.isEmpty() -> {
                            Text(
                                text = "No connections.\nClick + to add one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(uiState.profiles) { profile ->
                                    val isConnected = connectedProfileIds.contains(profile.id)
                                    val isConnecting = connectingProfileId == profile.id

                                    Column {
                                        ConnectionPanelRow(
                                            profileName = profile.name,
                                            isConnected = isConnected,
                                            isConnecting = isConnecting,
                                            onClick = {
                                                if (isConnecting) return@ConnectionPanelRow
                                                if (isConnected) {
                                                    viewModel.disconnect(
                                                        profileId = profile.id,
                                                        profileName = profile.name,
                                                        onStatusTextChanged = onStatusTextChanged,
                                                        onSessionClose = { profileId ->
                                                            sessionViewModel.closeSession(profileId)
                                                        },
                                                        onSetConnecting = { profileId ->
                                                            sessionViewModel.setConnecting(profileId)
                                                        },
                                                        onUpdateStatus = {},
                                                    )
                                                } else {
                                                    viewModel.connect(
                                                        profile = profile,
                                                        onStatusTextChanged = onStatusTextChanged,
                                                        onSessionOpen = { profileId, profileName, driver ->
                                                            sessionViewModel.openSession(profileId, profileName, driver)
                                                            sessionViewModel.setActiveProfile(profileId)
                                                        },
                                                        onSetConnecting = { profileId ->
                                                            sessionViewModel.setConnecting(profileId)
                                                        },
                                                    )
                                                }
                                            },
                                        )

                                        if (isConnected) {
                                            val schemaState = sessionStates[profile.id]?.schema
                                            val isExpanded = expandedSchemas.contains(profile.id)

                                            InlineSchemaSection(
                                                profileId = profile.id,
                                                profileName = profile.name,
                                                isExpanded = isExpanded,
                                                isLoading = schemaState?.isLoading ?: false,
                                                nodes = schemaState?.nodes ?: emptyList(),
                                                schemaMetadata = schemaState?.schemaMetadata,
                                                columnsCache = schemaState?.columnsCache ?: emptyMap(),
                                                sessionViewModel = sessionViewModel,
                                                dialogState = dialogStates[profile.id],
                                                onDialogStateChanged = { newState ->
                                                    dialogStates = dialogStates + (profile.id to newState)
                                                },
                                                onToggle = {
                                                    expandedSchemas = if (isExpanded) {
                                                        expandedSchemas - profile.id
                                                    } else {
                                                        expandedSchemas + profile.id
                                                    }
                                                },
                                                onRefresh = {
                                                    loadSchema(profile.id, profile.name)
                                                },
                                                onDoubleClickTable = { tableName ->
                                                    onOpenTableEditor(profile.id, tableName)
                                                },
                                                onStatusTextChanged = onStatusTextChanged,
                                                getDialectForDriver = ::getDialectForDriver,
                                                executeDDL = ::executeDDL,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanelRow(
    profileName: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(
                color = when {
                    isConnecting -> Color.Yellow
                    isConnected -> Color.Green
                    else -> Color.Gray
                },
            )
        }

        Text(
            text = profileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InlineSchemaSection(
    profileId: String,
    profileName: String,
    isExpanded: Boolean,
    isLoading: Boolean,
    nodes: List<SchemaTreeNode>,
    schemaMetadata: SchemaMetadata?,
    columnsCache: Map<String, SessionViewModel.ColumnCacheEntry>,
    sessionViewModel: SessionViewModel,
    dialogState: DialogState?,
    onDialogStateChanged: (DialogState) -> Unit,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onDoubleClickTable: (String) -> Unit,
    onStatusTextChanged: (String) -> Unit,
    getDialectForDriver: (DatabaseDriver) -> DDLDialect,
    executeDDL: suspend (DatabaseDriver, String) -> Result<Unit>,
) {
    val coroutineScope = rememberCoroutineScope()
    val driver = sessionViewModel.getDriver(profileId)
    val currentDialogState = dialogState ?: DialogState(profileId)

    fun updateDialogState(update: DialogState.() -> DialogState) {
        onDialogStateChanged(currentDialogState.update())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        FontAwesomeIcons.Solid.ChevronDown
                    } else {
                        FontAwesomeIcons.Solid.ChevronRight
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Schema",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(24.dp),
                enabled = !isLoading,
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Sync,
                    contentDescription = "Refresh schema",
                    modifier = Modifier.size(12.dp),
                    tint = if (isLoading) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(top = 4.dp),
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    nodes.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No tables found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        SchemaTree(
                            nodes = nodes,
                            modifier = Modifier.fillMaxSize(),
                            onNodeExpansionChanged = { node, isExpanding ->
                                if (isExpanding && node is SchemaTreeNode.Table && node.children.isEmpty() && driver != null) {
                                    coroutineScope.launch {
                                        val queryExecutor = QueryExecutor(driver)
                                        val columns = withContext(Dispatchers.IO) {
                                            queryExecutor.getColumns(node.label)
                                        }
                                        
                                        val columnNodes = columns.map { col ->
                                            val fkTarget = schemaMetadata?.foreignKeys
                                                ?.find { it.fromTable == node.label && it.fromColumn == col.name }
                                                ?.let { "${it.toTable}.${it.toColumn}" }
                                            
                                            SchemaTreeNode.Column(
                                                id = "column:${node.label}.${col.name}",
                                                label = col.name,
                                                type = col.type,
                                                foreignKeyTarget = fkTarget,
                                            )
                                        }
                                        
                                        val tableKey = schemaMetadata?.tables
                                            ?.find { it.name == node.label }
                                            ?.let { "${it.schema}.${it.name}" }
                                            ?: node.label
                                        
                                        sessionViewModel.updateSchemaState(profileId) { state ->
                                            val updatedCache = state.columnsCache + (tableKey to SessionViewModel.ColumnCacheEntry(
                                                loadedAtMs = System.currentTimeMillis(),
                                                columns = columnNodes,
                                            ))
                                            
                                            val updatedNodes = state.nodes.map { sectionNode ->
                                                if (sectionNode is SchemaTreeNode.Section && sectionNode.id == "section:tables") {
                                                    sectionNode.copy(
                                                        children = sectionNode.children.map { tableNode ->
                                                            if (tableNode.id == node.id) {
                                                                SchemaTreeNode.Table(
                                                                    id = tableNode.id,
                                                                    label = tableNode.label,
                                                                    children = columnNodes,
                                                                )
                                                            } else {
                                                                tableNode
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    sectionNode
                                                }
                                            }
                                            
                                            state.copy(
                                                nodes = updatedNodes,
                                                columnsCache = updatedCache,
                                            )
                                        }
                                    }
                                }
                            },
                            onViewData = { tableName ->
                                onDoubleClickTable(tableName)
                            },
                            onNewTable = {
                                updateDialogState {
                                    copy(showTableEditor = true, editingTable = null)
                                }
                            },
                            onEditTable = { tableName ->
                                updateDialogState {
                                    copy(showTableEditor = true, editingTable = tableName)
                                }
                            },
                            onDropTable = { tableName ->
                                if (driver == null) return@SchemaTree
                                coroutineScope.launch {
                                    val ddlResult = SchemaEditorViewModel.dropTableDDL(driver, tableName, cascade = true)
                                    ddlResult.onSuccess { ddl ->
                                        updateDialogState {
                                            copy(
                                                showDDLPreview = true,
                                                ddlSql = ddl,
                                                isDestructive = true,
                                                pendingExecution = { SchemaEditorViewModel.executeTableDrop(driver, ddl) },
                                            )
                                        }
                                    }.onFailure { error ->
                                        updateDialogState {
                                            copy(
                                                showError = true,
                                                errorMessage = error.message ?: "Failed to generate DROP DDL",
                                            )
                                        }
                                    }
                                }
                            },
                            onNewSequence = {
                                updateDialogState {
                                    copy(showSequenceEditor = true, editingSequence = null)
                                }
                            },
                            onEditSequence = { sequenceName ->
                                updateDialogState {
                                    copy(showSequenceEditor = true, editingSequence = sequenceName)
                                }
                            },
                            onDropSequence = { sequenceName ->
                                if (driver == null) return@SchemaTree
                                coroutineScope.launch {
                                    val ddlResult = SchemaEditorViewModel.dropSequenceDDL(driver, sequenceName, ifExists = true)
                                    ddlResult.onSuccess { ddl ->
                                        updateDialogState {
                                            copy(
                                                showDDLPreview = true,
                                                ddlSql = ddl,
                                                isDestructive = true,
                                                pendingExecution = { SchemaEditorViewModel.executeSequenceDrop(driver, ddl) },
                                            )
                                        }
                                    }.onFailure { error ->
                                        updateDialogState {
                                            copy(
                                                showError = true,
                                                errorMessage = error.message ?: "Failed to generate DROP DDL",
                                            )
                                        }
                                    }
                                }
                            },
                            onNewView = {
                                updateDialogState {
                                    copy(showViewEditor = true)
                                }
                            },
                            onDropView = { viewName ->
                                if (driver == null) return@SchemaTree
                                coroutineScope.launch {
                                    val dialect = getDialectForDriver(driver)
                                    val ddl = ViewDDLBuilder.buildDropView(dialect, viewName, ifExists = true)
                                    updateDialogState {
                                        copy(
                                            showDDLPreview = true,
                                            ddlSql = ddl,
                                            isDestructive = true,
                                            pendingExecution = { executeDDL(driver, ddl) },
                                        )
                                    }
                                }
                            },
                            onNewIndex = {
                                updateDialogState {
                                    copy(showIndexEditor = true)
                                }
                            },
                            onDropIndex = { indexName ->
                                if (driver == null) return@SchemaTree
                                coroutineScope.launch {
                                    val dialect = getDialectForDriver(driver)
                                    val ddl = IndexDDLBuilder.buildDropIndex(dialect, indexName, ifExists = true)
                                    updateDialogState {
                                        copy(
                                            showDDLPreview = true,
                                            ddlSql = ddl,
                                            isDestructive = true,
                                            pendingExecution = { executeDDL(driver, ddl) },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // Dialogs for this connection
    if (currentDialogState.showTableEditor && driver != null) {
        val allTables = schemaMetadata?.tables?.map { it.name } ?: emptyList()

        val existingIndexes = currentDialogState.editingTable?.let { tableName ->
            schemaMetadata?.indexDetails
                ?.filter { it.tableName == tableName }
                ?.map { idxMeta ->
                    com.dbeagle.ddl.IndexDefinition(
                        name = idxMeta.name,
                        tableName = idxMeta.tableName,
                        columns = idxMeta.columns,
                        unique = idxMeta.unique,
                    )
                } ?: emptyList()
        } ?: emptyList()

        val existingTableDef = currentDialogState.editingTable?.let { tableName ->
            schemaMetadata?.tables?.find { it.name == tableName }?.let { tableMetadata ->
                val columnDefs = tableMetadata.columns.map { col ->
                    val colType = inferColumnType(col.type)
                    com.dbeagle.ddl.ColumnDefinition(
                        name = col.name,
                        type = colType,
                        nullable = col.nullable,
                        defaultValue = col.defaultValue,
                    )
                }

                val foreignKeyDefs = schemaMetadata.foreignKeys
                    .filter { it.fromTable == tableName }
                    .map { fk ->
                        com.dbeagle.ddl.ForeignKeyDefinition(
                            name = null,
                            columns = listOf(fk.fromColumn),
                            refTable = fk.toTable,
                            refColumns = listOf(fk.toColumn),
                            onDelete = null,
                            onUpdate = null,
                        )
                    }

                com.dbeagle.ddl.TableDefinition(
                    name = tableMetadata.name,
                    columns = columnDefs,
                    primaryKey = tableMetadata.primaryKey.takeIf { it.isNotEmpty() },
                    foreignKeys = foreignKeyDefs,
                    uniqueConstraints = emptyList(),
                )
            }
        }

        TableEditorDialog(
            existingTable = existingTableDef,
            existingIndexes = existingIndexes,
            allTables = allTables,
            onDismiss = {
                updateDialogState { copy(showTableEditor = false, editingTable = null) }
            },
            onSave = { tableDef, newIndexes ->
                coroutineScope.launch {
                    try {
                        val isCreateMode = currentDialogState.editingTable == null
                        val ddlResult = if (isCreateMode) {
                            SchemaEditorViewModel.createTableDDL(driver, tableDef)
                        } else {
                            val oldTableDef = existingTableDef ?: return@launch

                            val addedColumns = tableDef.columns.filter { newCol ->
                                oldTableDef.columns.none { it.name == newCol.name }
                            }
                            val droppedColumns = oldTableDef.columns.filter { oldCol ->
                                tableDef.columns.none { it.name == oldCol.name }
                            }.map { it.name }

                            val addedConstraints = buildList {
                                if (tableDef.primaryKey != null && tableDef.primaryKey != oldTableDef.primaryKey) {
                                    add(ConstraintDefinition.PrimaryKey(tableDef.primaryKey!!))
                                }

                                tableDef.foreignKeys.forEach { newFk ->
                                    val exists = oldTableDef.foreignKeys.any { oldFk ->
                                        oldFk.columns == newFk.columns &&
                                            oldFk.refTable == newFk.refTable &&
                                            oldFk.refColumns == newFk.refColumns
                                    }
                                    if (!exists) {
                                        add(ConstraintDefinition.ForeignKey(newFk))
                                    }
                                }

                                tableDef.uniqueConstraints.forEach { newUnique ->
                                    val exists = oldTableDef.uniqueConstraints.any { it == newUnique }
                                    if (!exists) {
                                        add(ConstraintDefinition.Unique(null, newUnique))
                                    }
                                }
                            }

                            val droppedConstraints = buildList {
                                if (oldTableDef.primaryKey != null &&
                                    (tableDef.primaryKey == null || tableDef.primaryKey != oldTableDef.primaryKey)
                                ) {
                                    add("${currentDialogState.editingTable}_pkey")
                                }

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

                                oldTableDef.uniqueConstraints.forEachIndexed { idx, oldUnique ->
                                    val stillExists = tableDef.uniqueConstraints.any { it == oldUnique }
                                    if (!stillExists) {
                                        add("${currentDialogState.editingTable}_unique_$idx")
                                    }
                                }
                            }

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
                                droppedIndexes = droppedIndexes,
                            )
                            SchemaEditorViewModel.alterTableDDL(driver, currentDialogState.editingTable, changes)
                        }

                        ddlResult.onSuccess { ddl ->
                            updateDialogState {
                                copy(
                                    showTableEditor = false,
                                    editingTable = null,
                                    showDDLPreview = true,
                                    ddlSql = ddl,
                                    isDestructive = false,
                                    pendingExecution = if (isCreateMode) {
                                        { SchemaEditorViewModel.executeTableCreate(driver, ddl) }
                                    } else {
                                        { SchemaEditorViewModel.executeTableAlter(driver, ddl) }
                                    },
                                )
                            }
                        }.onFailure { error ->
                            updateDialogState {
                                copy(
                                    showError = true,
                                    errorMessage = error.message ?: "Failed to generate DDL",
                                )
                            }
                        }
                    } catch (e: Exception) {
                        updateDialogState {
                            copy(
                                showError = true,
                                errorMessage = e.message ?: "Unknown error",
                            )
                        }
                    }
                }
            },
        )
    }

    if (currentDialogState.showSequenceEditor && driver != null) {
        val existingSeq = currentDialogState.editingSequence?.let { seqName ->
            schemaMetadata?.sequences?.find { it.name == seqName }
        }

        SequenceEditorDialog(
            existingSequence = existingSeq,
            onDismiss = {
                updateDialogState { copy(showSequenceEditor = false, editingSequence = null) }
            },
            onSave = { seqMetadata ->
                coroutineScope.launch {
                    try {
                        val isCreateMode = currentDialogState.editingSequence == null
                        val ddlResult = if (isCreateMode) {
                            SchemaEditorViewModel.createSequenceDDL(driver, seqMetadata)
                        } else {
                            val oldSeq = existingSeq ?: return@launch
                            val changes = com.dbeagle.ddl.SequenceChanges(
                                increment = if (seqMetadata.increment != oldSeq.increment) seqMetadata.increment else null,
                                minValue = if (seqMetadata.minValue != oldSeq.minValue) seqMetadata.minValue else null,
                                maxValue = if (seqMetadata.maxValue != oldSeq.maxValue) seqMetadata.maxValue else null,
                                restart = null,
                            )
                            SchemaEditorViewModel.alterSequenceDDL(driver, currentDialogState.editingSequence, changes)
                        }

                        ddlResult.onSuccess { ddl ->
                            updateDialogState {
                                copy(
                                    showSequenceEditor = false,
                                    editingSequence = null,
                                    showDDLPreview = true,
                                    ddlSql = ddl,
                                    isDestructive = false,
                                    pendingExecution = if (isCreateMode) {
                                        { SchemaEditorViewModel.executeSequenceCreate(driver, ddl) }
                                    } else {
                                        { SchemaEditorViewModel.executeSequenceAlter(driver, ddl) }
                                    },
                                )
                            }
                        }.onFailure { error ->
                            updateDialogState {
                                copy(
                                    showError = true,
                                    errorMessage = error.message ?: "Failed to generate DDL",
                                )
                            }
                        }
                    } catch (e: Exception) {
                        updateDialogState {
                            copy(
                                showError = true,
                                errorMessage = e.message ?: "Unknown error",
                            )
                        }
                    }
                }
            },
        )
    }

    if (currentDialogState.showViewEditor && driver != null) {
        val dialect = getDialectForDriver(driver)
        ViewEditorDialog(
            dialect = dialect,
            onDismiss = {
                updateDialogState { copy(showViewEditor = false) }
            },
            onSave = { ddl ->
                updateDialogState {
                    copy(
                        showViewEditor = false,
                        showDDLPreview = true,
                        ddlSql = ddl,
                        isDestructive = false,
                        pendingExecution = { executeDDL(driver, ddl) },
                    )
                }
            },
        )
    }

    if (currentDialogState.showIndexEditor && driver != null) {
        val dialect = getDialectForDriver(driver)
        val allTables = schemaMetadata?.tables?.map { "${it.schema}.${it.name}" } ?: emptyList()

        IndexEditorDialog(
            dialect = dialect,
            tables = allTables,
            getColumnsForTable = { tableName ->
                withContext(Dispatchers.IO) {
                    val queryExecutor = QueryExecutor(driver)
                    queryExecutor.getColumns(tableName).map { it.name }
                }
            },
            onDismiss = {
                updateDialogState { copy(showIndexEditor = false) }
            },
            onCreate = { ddl ->
                executeDDL(driver, ddl).also {
                    if (it.isSuccess) {
                        updateDialogState { copy(showIndexEditor = false) }
                        onRefresh()
                    }
                }
            },
        )
    }

    if (currentDialogState.showDDLPreview) {
        DDLPreviewDialog(
            ddlSql = currentDialogState.ddlSql,
            isDestructive = currentDialogState.isDestructive,
            onDismiss = {
                updateDialogState {
                    copy(
                        showDDLPreview = false,
                        ddlSql = "",
                        isDestructive = false,
                        pendingExecution = null,
                    )
                }
            },
            onExecute = {
                coroutineScope.launch {
                    val execution = currentDialogState.pendingExecution
                    if (execution != null) {
                        onStatusTextChanged("Status: Executing DDL ($profileName)")
                        try {
                            val result = execution()
                            result.onSuccess {
                                onStatusTextChanged("Status: DDL executed successfully ($profileName)")
                                updateDialogState {
                                    copy(
                                        showDDLPreview = false,
                                        ddlSql = "",
                                        isDestructive = false,
                                        pendingExecution = null,
                                    )
                                }
                                onRefresh()
                            }.onFailure { error ->
                                onStatusTextChanged("Status: DDL execution failed ($profileName)")
                                updateDialogState {
                                    copy(
                                        showDDLPreview = false,
                                        ddlSql = "",
                                        isDestructive = false,
                                        pendingExecution = null,
                                        showError = true,
                                        errorMessage = error.message ?: "Failed to execute DDL",
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            onStatusTextChanged("Status: DDL execution failed ($profileName)")
                            updateDialogState {
                                copy(
                                    showDDLPreview = false,
                                    ddlSql = "",
                                    isDestructive = false,
                                    pendingExecution = null,
                                    showError = true,
                                    errorMessage = e.message ?: "Failed to execute DDL",
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    if (currentDialogState.showError) {
        AlertDialog(
            onDismissRequest = {
                updateDialogState { copy(showError = false, errorMessage = "") }
            },
            title = { Text("DDL Error") },
            text = { Text(currentDialogState.errorMessage) },
            confirmButton = {
                TextButton(onClick = {
                    updateDialogState { copy(showError = false, errorMessage = "") }
                }) {
                    Text("OK")
                }
            },
        )
    }
}
