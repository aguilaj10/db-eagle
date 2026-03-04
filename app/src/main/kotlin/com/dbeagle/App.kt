package com.dbeagle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dbeagle.di.appModule
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.edit.InlineUpdate
import com.dbeagle.favorites.FileFavoritesRepository
import com.dbeagle.favorites.FavoritesRepository
import com.dbeagle.history.FileQueryHistoryRepository
import com.dbeagle.history.QueryHistoryRepository
import com.dbeagle.model.FavoriteQuery
import com.dbeagle.model.QueryHistoryEntry
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.QueryResult
import com.dbeagle.query.QueryExecutor
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin

enum class NavigationTab(val title: String) {
    Connections("Connections"),
    QueryEditor("Query Editor"),
    SchemaBrowser("Schema Browser"),
    Favorites("Favorites"),
    History("History")
}

@OptIn(ExperimentalMaterial3Api::class)
fun main() {
    startKoin {
        modules(appModule)
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        var selectedTab by remember { mutableStateOf(NavigationTab.Connections) }
        var statusText by remember { mutableStateOf("Status: Disconnected") }

        var activeProfileName by remember { mutableStateOf<String?>(null) }
        var activeDriver by remember { mutableStateOf<DatabaseDriver?>(null) }
        
        val historyRepository = remember { FileQueryHistoryRepository() }
        val favoritesRepository = remember { FileFavoritesRepository() }
        var queryEditorSql by remember { mutableStateOf("SELECT * FROM users;\n") }
        var showSaveFavoriteDialog by remember { mutableStateOf(false) }

        if (showSaveFavoriteDialog) {
            SaveFavoriteDialog(
                initialQuery = queryEditorSql,
                onDismiss = { showSaveFavoriteDialog = false },
                onSave = { name, tags ->
                    val favorite = FavoriteQuery(
                        name = name,
                        query = queryEditorSql,
                        tags = tags
                    )
                    favoritesRepository.save(favorite)
                    statusText = "Status: Saved to favorites"
                    showSaveFavoriteDialog = false
                }
            )
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "DB Eagle",
            state = windowState
        ) {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("DB Eagle") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    },
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = statusText,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Left sidebar
                        Box(
                            modifier = Modifier
                                .width(250.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = { /* Placeholder: New Connection */ },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("New Connection")
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                    text = "Connections",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "(Active connection list placeholder)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // Center content area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedTab.ordinal,
                                edgePadding = 8.dp
                            ) {
                                NavigationTab.entries.forEach { tab ->
                                    Tab(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        text = { Text(tab.title) }
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when (selectedTab) {
                                    NavigationTab.Connections -> {
                                        com.dbeagle.ui.ConnectionManagerScreen(
                                            onStatusTextChanged = { statusText = it },
                                            onActiveConnectionChanged = { _, name, driver ->
                                                activeProfileName = name
                                                activeDriver = driver
                                            }
                                        )
                                    }
                                    NavigationTab.QueryEditor -> {
                                        var lastExecutedSql by remember { mutableStateOf<String?>(null) }
                                        var lastQueryResult by remember { mutableStateOf<QueryResult.Success?>(null) }
                                        var columns by remember { mutableStateOf<List<String>>(emptyList()) }
                                        var rows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
                                        var isRunning by remember { mutableStateOf(false) }
                                        var queryError by remember { mutableStateOf<String?>(null) }
                                        var editError by remember { mutableStateOf<String?>(null) }
                                        val coroutineScope = rememberCoroutineScope()

                                        var showExportDialog by remember { mutableStateOf(false) }

                                        if (showExportDialog) {
                                            com.dbeagle.ui.ExportDialog(
                                                onDismiss = { showExportDialog = false },
                                                onExportRequested = { format, path, onProgress ->
                                                    val result = lastQueryResult
                                                    if (result == null) {
                                                        statusText = "Status: No query result to export"
                                                        return@ExportDialog
                                                    }
                                                    
                                                    try {
                                                        val outputFile = java.io.File(path)
                                                        val exporter = when (format) {
                                                            com.dbeagle.ui.ExportFormat.CSV -> com.dbeagle.export.CsvExporter()
                                                            com.dbeagle.ui.ExportFormat.JSON -> com.dbeagle.export.JsonExporter()
                                                            com.dbeagle.ui.ExportFormat.SQL -> com.dbeagle.export.SqlExporter()
                                                        }
                                                        
                                                        exporter.export(outputFile, result, result.resultSet) { rowCount, isDone ->
                                                            onProgress(rowCount, isDone)
                                                            if (isDone) {
                                                                statusText = "Status: Exported $rowCount rows to $path"
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        statusText = "Status: Export failed: ${e.message}"
                                                    }
                                                }
                                            )
                                        }

                                        Column(modifier = Modifier.fillMaxSize()) {
                                         if (queryError != null) {
                                             AlertDialog(
                                                    onDismissRequest = { queryError = null },
                                                    title = { Text("Query Error") },
                                                    text = { Text(queryError ?: "") },
                                                    confirmButton = {
                                                        TextButton(onClick = { queryError = null }) {
                                                            Text("OK")
                                                        }
                                                    }
                                             )
                                         }

                                         if (editError != null) {
                                             AlertDialog(
                                                 onDismissRequest = { editError = null },
                                                 title = { Text("Edit Error") },
                                                 text = { Text(editError ?: "") },
                                                 confirmButton = {
                                                     TextButton(onClick = { editError = null }) {
                                                         Text("OK")
                                                     }
                                                 }
                                             )
                                         }

                                            com.dbeagle.ui.SQLEditor(
                                                sql = queryEditorSql,
                                                onSqlChange = { queryEditorSql = it },
                                                 onRun = {
                                                    if (isRunning) return@SQLEditor
                                                    val driver = activeDriver
                                                    if (driver == null) {
                                                        statusText = "Status: No active connection"
                                                        return@SQLEditor
                                                    }

                                                     coroutineScope.launch {
                                                         isRunning = true
                                                         queryError = null
                                                         val name = activeProfileName ?: "Connection"
                                                         statusText = "Status: Running query ($name)"

                                                         val startNs = System.nanoTime()
                                                           try {
                                                               when (val r = QueryExecutor(driver).execute(queryEditorSql)) {
                                                                   is QueryResult.Success -> {
                                                                       lastExecutedSql = queryEditorSql
                                                                       lastQueryResult = r
                                                                       columns = r.columnNames
                                                                       rows = r.rows.map { rowMap ->
                                                                           r.columnNames.map { col -> rowMap[col] ?: "" }
                                                                       }
                                                                       val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                                                       statusText = "Status: ${rows.size} row(s) in ${durationMs}ms"
                                                                       
                                                                       activeProfileName?.let { profileId ->
                                                                           historyRepository.add(
                                                                               QueryHistoryEntry(
                                                                                   query = queryEditorSql,
                                                                                   durationMs = durationMs,
                                                                                   connectionProfileId = profileId
                                                                               )
                                                                           )
                                                                       }
                                                                   }
                                                                  is QueryResult.Error -> {
                                                                      val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                                                      statusText = "Status: Error in ${durationMs}ms: ${r.message}"
                                                                      queryError = r.message
                                                                      
                                                                      activeProfileName?.let { profileId ->
                                                                          historyRepository.add(
                                                                              QueryHistoryEntry(
                                                                                  query = queryEditorSql,
                                                                                  durationMs = durationMs,
                                                                                  connectionProfileId = profileId
                                                                              )
                                                                          )
                                                                      }
                                                                  }
                                                              }
                                                          } catch (e: Exception) {
                                                              val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                                              statusText = "Status: Error in ${durationMs}ms: ${e.message ?: "Error"}"
                                                              queryError = e.message ?: "Error"
                                                          } finally {
                                                              isRunning = false
                                                          }
                                                     }
                                                 },
                                                isRunning = isRunning,
                                                onClear = { queryEditorSql = "" },
                                                onSaveToFavorites = { showSaveFavoriteDialog = true },
                                                modifier = Modifier.weight(0.4f)
                                            )
                                            
                                            HorizontalDivider(thickness = 2.dp)
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = { showExportDialog = true },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Export Data", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            
                                             com.dbeagle.ui.ResultGrid(
                                                 columns = columns,
                                                 rows = rows,
                                                 pageSize = 25,
                                                 modifier = Modifier.weight(0.6f),
                                                 onCellCommit = { _, columnName, newValue, rowSnapshot ->
                                                     val driver = activeDriver
                                                     if (driver == null) {
                                                         statusText = "Status: No active connection"
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
                                                         id = idValue
                                                     )

                                                     when (val r = QueryExecutor(driver).execute(stmt.sql, stmt.params)) {
                                                         is QueryResult.Success -> {
                                                             statusText = "Status: Updated $table.$columnName for id=$idValue"
                                                             Result.success(Unit)
                                                         }
                                                         is QueryResult.Error -> {
                                                             val msg = r.message
                                                             editError = msg
                                                             statusText = "Status: Update failed: $msg"
                                                             Result.failure(IllegalStateException(msg))
                                                         }
                                                     }
                                                 }
                                             )
                                         }
                                     }
                                    NavigationTab.SchemaBrowser -> {
                                        val coroutineScope = rememberCoroutineScope()

                                        val ttlMs = 5 * 60 * 1000L
                                        var isLoadingSchema by remember { mutableStateOf(false) }
                                        var schemaLoadedAtMs by remember { mutableStateOf<Long?>(null) }
                                        var schemaNodes by remember { mutableStateOf<List<com.dbeagle.ui.SchemaTreeNode>>(emptyList()) }
                                        var schemaDialogError by remember { mutableStateOf<String?>(null) }

                                        data class ColumnCacheEntry(
                                            val loadedAtMs: Long,
                                            val columns: List<com.dbeagle.ui.SchemaTreeNode.Column>
                                        )

                                        var columnsCache by remember {
                                            mutableStateOf<Map<String, ColumnCacheEntry>>(emptyMap())
                                        }

                                        fun isExpired(loadedAt: Long?): Boolean {
                                            if (loadedAt == null) return true
                                            return (System.currentTimeMillis() - loadedAt) > ttlMs
                                        }

                                        fun buildTree(schema: SchemaMetadata): List<com.dbeagle.ui.SchemaTreeNode> {
                                            val tables = schema.tables
                                                .sortedWith(compareBy({ it.schema }, { it.name }))
                                                .map { t ->
                                                    val tableKey = "${t.schema}.${t.name}"
                                                    val cached = columnsCache[tableKey]?.columns ?: emptyList()
                                                    com.dbeagle.ui.SchemaTreeNode.Table(
                                                        id = "table:$tableKey",
                                                        label = t.name,
                                                        children = cached
                                                    )
                                                }

                                            val views = schema.views
                                                .sorted()
                                                .map { v ->
                                                    com.dbeagle.ui.SchemaTreeNode.View(
                                                        id = "view:$v",
                                                        label = v
                                                    )
                                                }

                                            val indexes = schema.indexes
                                                .sorted()
                                                .map { idx ->
                                                    com.dbeagle.ui.SchemaTreeNode.Index(
                                                        id = "index:$idx",
                                                        label = idx
                                                    )
                                                }

                                            return listOf(
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "section:tables",
                                                    label = "Tables",
                                                    children = tables
                                                ),
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "section:views",
                                                    label = "Views",
                                                    children = views
                                                ),
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "section:indexes",
                                                    label = "Indexes",
                                                    children = indexes
                                                )
                                            )
                                        }

                                        fun updateTableChildren(
                                            tableKey: String,
                                            newChildren: List<com.dbeagle.ui.SchemaTreeNode.Column>
                                        ) {
                                            schemaNodes = schemaNodes.map { node ->
                                                if (node is com.dbeagle.ui.SchemaTreeNode.Section && node.id == "section:tables") {
                                                    com.dbeagle.ui.SchemaTreeNode.Section(
                                                        id = node.id,
                                                        label = node.label,
                                                        children = node.children.map { child ->
                                                            if (
                                                                child is com.dbeagle.ui.SchemaTreeNode.Table &&
                                                                child.id == "table:$tableKey"
                                                            ) {
                                                                com.dbeagle.ui.SchemaTreeNode.Table(
                                                                    id = child.id,
                                                                    label = child.label,
                                                                    children = newChildren
                                                                )
                                                            } else {
                                                                child
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    node
                                                }
                                            }
                                        }

                                        fun forceRefresh() {
                                            schemaLoadedAtMs = null
                                            columnsCache = emptyMap()
                                            schemaNodes = emptyList()
                                        }

                                        fun ensureSchemaLoaded(force: Boolean) {
                                            val driver = activeDriver
                                            if (driver == null) {
                                                schemaNodes = emptyList()
                                                schemaLoadedAtMs = null
                                                columnsCache = emptyMap()
                                                return
                                            }

                                            if (!force && !isExpired(schemaLoadedAtMs) && schemaNodes.isNotEmpty()) return
                                            if (isLoadingSchema) return

                                            coroutineScope.launch {
                                                isLoadingSchema = true
                                                val name = activeProfileName ?: "Connection"
                                                statusText = "Status: Loading schema ($name)"
                                                try {
                                                    val schema = driver.getSchema()
                                                    schemaNodes = buildTree(schema)
                                                    schemaLoadedAtMs = System.currentTimeMillis()
                                                    statusText = "Status: Schema loaded ($name)"
                                                } catch (e: Exception) {
                                                    statusText = "Status: Failed to load schema: ${e.message ?: "Error"}"
                                                    schemaDialogError = e.message ?: "Failed to load schema"
                                                } finally {
                                                    isLoadingSchema = false
                                                }
                                            }
                                        }

                                        LaunchedEffect(selectedTab, activeDriver) {
                                            if (selectedTab != NavigationTab.SchemaBrowser) return@LaunchedEffect
                                            if (activeDriver == null) {
                                                schemaNodes = emptyList()
                                                schemaLoadedAtMs = null
                                                columnsCache = emptyMap()
                                                return@LaunchedEffect
                                            }
                                            forceRefresh()
                                            ensureSchemaLoaded(force = false)
                                        }

                                        if (schemaDialogError != null) {
                                            AlertDialog(
                                                onDismissRequest = { schemaDialogError = null },
                                                title = { Text("Schema Error") },
                                                text = { Text(schemaDialogError ?: "") },
                                                confirmButton = {
                                                    TextButton(onClick = { schemaDialogError = null }) {
                                                        Text("OK")
                                                    }
                                                }
                                            )
                                        }

                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                val hasConnection = activeDriver != null
                                                Button(
                                                    onClick = {
                                                        if (!hasConnection) return@Button
                                                        forceRefresh()
                                                        ensureSchemaLoaded(force = true)
                                                    },
                                                    enabled = hasConnection && !isLoadingSchema,
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Refresh", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }

                                            if (activeDriver == null) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "No active connection. Connect to browse schema.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else if (isLoadingSchema && schemaNodes.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator()
                                                }
                                            } else {
                                                com.dbeagle.ui.SchemaTree(
                                                    nodes = schemaNodes,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onNodeExpansionChanged = { node, expanded ->
                                                        if (!expanded) return@SchemaTree
                                                        if (node !is com.dbeagle.ui.SchemaTreeNode.Table) return@SchemaTree

                                                        val driver = activeDriver ?: return@SchemaTree

                                                        val tableKey = node.id.removePrefix("table:")
                                                        val tableName = node.label
                                                        val cached = columnsCache[tableKey]
                                                        if (cached != null && !isExpired(cached.loadedAtMs)) {
                                                            updateTableChildren(tableKey, cached.columns)
                                                            return@SchemaTree
                                                        }

                                                        coroutineScope.launch {
                                                            val name = activeProfileName ?: "Connection"
                                                            statusText = "Status: Loading columns ($name: $tableName)"
                                                            try {
                                                                val cols = driver.getColumns(tableName)
                                                                    .sortedBy { it.name }
                                                                    .map { c ->
                                                                        com.dbeagle.ui.SchemaTreeNode.Column(
                                                                            id = "col:$tableKey.${c.name}",
                                                                            label = c.name,
                                                                            type = c.type
                                                                        )
                                                                    }
                                                                val now = System.currentTimeMillis()
                                                                columnsCache = columnsCache + (tableKey to ColumnCacheEntry(now, cols))
                                                                updateTableChildren(tableKey, cols)
                                                                statusText = "Status: Columns loaded ($name: $tableName)"
                                                            } catch (e: Exception) {
                                                                statusText = "Status: Failed to load columns: ${e.message ?: "Error"}"
                                                                schemaDialogError = e.message ?: "Failed to load columns"
                                                            }
                                                        }
                                                    },
                                                    onCopyName = { name -> println("App: Copy Name -> $name") },
                                                    onViewData = { name -> println("App: View Data -> $name") }
                                                )
                                            }
                                        }
                                    }
                                    NavigationTab.Favorites -> {
                                        com.dbeagle.ui.FavoritesScreen(
                                            repository = favoritesRepository,
                                            onLoadQuery = { query ->
                                                queryEditorSql = query
                                                selectedTab = NavigationTab.QueryEditor
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    NavigationTab.History -> {
                                        com.dbeagle.ui.HistoryScreen(
                                            repository = historyRepository,
                                            onLoadQuery = { query ->
                                                queryEditorSql = query
                                                selectedTab = NavigationTab.QueryEditor
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                     else -> {
                                         Text(
                                             text = "${selectedTab.title} Content\n(Placeholder)",
                                             style = MaterialTheme.typography.headlineSmall,
                                             color = MaterialTheme.colorScheme.onBackground
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

@Composable
private fun SaveFavoriteDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSave: (name: String, tags: List<String>) -> Unit
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
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("sql, reports, etc.") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Query: ${initialQuery.take(100)}${if (initialQuery.length > 100) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

