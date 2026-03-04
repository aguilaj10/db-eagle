package com.dbeagle

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dbeagle.crash.CrashReporter
import com.dbeagle.di.appModule
import com.dbeagle.edit.InlineUpdate
import com.dbeagle.error.ErrorHandler
import com.dbeagle.favorites.FileFavoritesRepository
import com.dbeagle.history.FileQueryHistoryRepository
import com.dbeagle.model.FavoriteQuery
import com.dbeagle.model.QueryHistoryEntry
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.query.QueryExecutor
import com.dbeagle.session.SessionViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

private data class MemoryStats(
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val maxBytes: Long,
) {
    val usedMb: Long get() = usedBytes / (1024L * 1024L)
    val totalMb: Long get() = totalBytes / (1024L * 1024L)
    val maxMb: Long get() = maxBytes / (1024L * 1024L)
}

private fun readMemoryStats(): MemoryStats {
    val rt = Runtime.getRuntime()
    val total = rt.totalMemory()
    val free = rt.freeMemory()
    val used = total - free
    val max = rt.maxMemory()
    return MemoryStats(
        usedBytes = used,
        freeBytes = free,
        totalBytes = total,
        maxBytes = max,
    )
}

enum class NavigationTab(val title: String) {
    Connections("Connections"),
    QueryEditor("Query Editor"),
    SchemaBrowser("Schema Browser"),
    Favorites("Favorites"),
    History("History"),
    Settings("Settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
fun main() {
    CrashReporter.install()
    val logger = LoggerFactory.getLogger("com.dbeagle.App")
    logger.info("DB Eagle starting...")

    startKoin {
        modules(appModule)
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        var selectedTab by remember { mutableStateOf(NavigationTab.Connections) }
        var statusText by remember { mutableStateOf("Status: Disconnected") }

        val sessionViewModel = remember { SessionViewModel() }
        val sessionOrder by sessionViewModel.sessionOrder.collectAsState()
        val sessionStates by sessionViewModel.sessionStates.collectAsState()
        val activeProfileId by sessionViewModel.activeProfileId.collectAsState()

        val activeSession = activeProfileId?.let { sessionStates[it] }
        val activeDriver = activeProfileId?.let { sessionViewModel.getDriver(it) }
        val activeProfileName = activeSession?.profileName

        var poolStats by remember { mutableStateOf<DatabaseConnectionPool.PoolStats?>(null) }
        var memoryStats by remember { mutableStateOf(readMemoryStats()) }

        LaunchedEffect(activeProfileId) {
            while (true) {
                poolStats = activeProfileId?.let { DatabaseConnectionPool.getPoolStats(it) }
                memoryStats = readMemoryStats()
                delay(1_000)
            }
        }

        var scratchSql by remember { mutableStateOf(SessionViewModel.DEFAULT_SQL) }
        var favoriteQueryDraft by remember { mutableStateOf("") }

        val appCoroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val historyRepository = remember { FileQueryHistoryRepository() }
        val favoritesRepository = remember { FileFavoritesRepository() }
        var showSaveFavoriteDialog by remember { mutableStateOf(false) }
        var triggerNewConnection by remember { mutableStateOf(false) }

        if (showSaveFavoriteDialog) {
            SaveFavoriteDialog(
                initialQuery = favoriteQueryDraft,
                onDismiss = { showSaveFavoriteDialog = false },
                onSave = { name, tags ->
                    val favorite = FavoriteQuery(
                        name = name,
                        query = favoriteQueryDraft,
                        tags = tags,
                    )
                    favoritesRepository.save(favorite)
                    statusText = "Status: Saved to favorites"
                    showSaveFavoriteDialog = false
                },
            )
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "DB Eagle",
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyUp && (event.isCtrlPressed || event.isMetaPressed)) {
                    when (event.key) {
                        Key.N -> {
                            selectedTab = NavigationTab.Connections
                            triggerNewConnection = true
                            true
                        }
                        Key.W -> {
                            activeProfileId?.let { pid ->
                                appCoroutineScope.launch {
                                    sessionViewModel.closeSession(pid)
                                }
                            }
                            true
                        }
                        Key.Comma -> {
                            selectedTab = NavigationTab.Settings
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        ) {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("DB Eagle") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            actions = {
                                IconButton(onClick = { selectedTab = NavigationTab.Settings }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                    )
                                }
                            },
                        )
                    },
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                )

                                Text(
                                    text = "Mem: ${memoryStats.usedMb}MB / ${memoryStats.maxMb}MB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 16.dp),
                                )

                                val stats = poolStats
                                val poolText = if (stats == null) {
                                    "Pool: n/a"
                                } else {
                                    "Pool a=${stats.active} i=${stats.idle} t=${stats.total} w=${stats.waiting}"
                                }
                                val poolColor = when {
                                    stats == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    stats.waiting > 0 -> MaterialTheme.colorScheme.error
                                    stats.total > 0 && stats.active == stats.total -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                Text(
                                    text = poolText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = poolColor,
                                )

                                Spacer(Modifier.width(16.dp))

                                TextButton(
                                    onClick = {
                                        val crashLog = CrashReporter.readCrashLog()
                                        if (crashLog != null) {
                                            try {
                                                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                                val selection = java.awt.datatransfer.StringSelection(crashLog)
                                                clipboard.setContents(selection, selection)
                                                statusText = "Status: Crash log copied to clipboard"
                                                logger.info("User action: Copied crash log to clipboard")
                                            } catch (e: Exception) {
                                                statusText = "Status: Failed to copy to clipboard"
                                                logger.warn("Failed to copy crash log to clipboard", e)
                                            }
                                        } else {
                                            statusText = "Status: No crash log found"
                                            logger.info("User action: Attempted to copy crash log, but none exists")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Report Issue",
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Report Issue",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        // Left sidebar
                        Box(
                            modifier = Modifier
                                .width(250.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Button(
                                    onClick = {
                                        selectedTab = NavigationTab.Connections
                                        triggerNewConnection = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("New Connection")
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                    text = "Connections",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "(Active connection list placeholder)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )

                        // Center content area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            if (sessionOrder.isNotEmpty()) {
                                val selectedConnectionTabIndex = sessionOrder.indexOf(activeProfileId).let { idx ->
                                    if (idx >= 0) idx else 0
                                }

                                ScrollableTabRow(
                                    selectedTabIndex = selectedConnectionTabIndex,
                                    edgePadding = 8.dp,
                                ) {
                                    sessionOrder.forEach { profileId ->
                                        val label = sessionStates[profileId]?.profileName ?: profileId.take(8)
                                        Tab(
                                            selected = activeProfileId == profileId,
                                            onClick = { sessionViewModel.setActiveProfile(profileId) },
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(label)
                                                    Spacer(Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            appCoroutineScope.launch {
                                                                sessionViewModel.closeSession(profileId)
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Close connection")
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            ScrollableTabRow(
                                selectedTabIndex = selectedTab.ordinal,
                                edgePadding = 8.dp,
                            ) {
                                NavigationTab.entries.forEach { tab ->
                                    Tab(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        text = { Text(tab.title) },
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (selectedTab) {
                                    NavigationTab.Connections -> {
                                        com.dbeagle.ui.ConnectionManagerScreen(
                                            sessionViewModel = sessionViewModel,
                                            onStatusTextChanged = { statusText = it },
                                            snackbarHostState = snackbarHostState,
                                            coroutineScope = appCoroutineScope,
                                            triggerNewConnection = triggerNewConnection,
                                            onNewConnectionTriggered = { triggerNewConnection = false },
                                        )
                                    }
                                    NavigationTab.QueryEditor -> {
                                        val coroutineScope = rememberCoroutineScope()
                                        var isRunning by remember(activeProfileId) { mutableStateOf(false) }
                                        var queryJob by remember(activeProfileId) { mutableStateOf<Job?>(null) }
                                        var queryError by remember(activeProfileId) { mutableStateOf<String?>(null) }
                                        var editError by remember(activeProfileId) { mutableStateOf<String?>(null) }
                                        var showExportDialog by remember(activeProfileId) { mutableStateOf(false) }

                                        val sqlText = activeSession?.queryEditorSql ?: scratchSql
                                        val lastExecutedSql = activeSession?.lastExecutedSql
                                        val lastQueryResult = activeSession?.lastQueryResult
                                        val columns = activeSession?.resultColumns ?: emptyList()
                                        val rows = activeSession?.resultRows ?: emptyList()

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

                                            com.dbeagle.ui.SQLEditor(
                                                sql = sqlText,
                                                onSqlChange = {
                                                    val pid = activeProfileId
                                                    if (pid == null) {
                                                        scratchSql = it
                                                    } else {
                                                        sessionViewModel.updateQueryEditorSql(pid, it)
                                                    }
                                                },
                                                onCancel = {
                                                    queryJob?.cancel()
                                                    isRunning = false
                                                    statusText = "Status: Query canceled"
                                                },
                                                onRun = {
                                                    if (isRunning) return@SQLEditor
                                                    val driver = activeDriver
                                                    if (driver == null) {
                                                        statusText = "Status: No active connection"
                                                        return@SQLEditor
                                                    }

                                                    queryJob = coroutineScope.launch {
                                                        isRunning = true
                                                        queryError = null
                                                        val name = activeProfileName ?: "Connection"
                                                        statusText = "Status: Running query ($name)"

                                                        val pid = activeProfileId
                                                        if (pid == null) {
                                                            statusText = "Status: No active connection"
                                                            isRunning = false
                                                            return@launch
                                                        }

                                                        sessionViewModel.clearQueryResult(pid)

                                                        val sqlToRun = sessionStates[pid]?.queryEditorSql ?: ""

                                                        val startNs = System.nanoTime()
                                                        try {
                                                            val r = withContext(Dispatchers.IO) { QueryExecutor(driver).execute(sqlToRun) }
                                                            when (r) {
                                                                is QueryResult.Success -> {
                                                                    sessionViewModel.recordQueryResult(pid, sqlToRun, r)
                                                                    val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                                                    statusText = "Status: ${r.rows.size} row(s) in ${durationMs}ms"

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
                                                                    statusText = "Status: Error in ${durationMs}ms: ${r.message}"
                                                                    ErrorHandler.showQueryError(
                                                                        snackbarHostState,
                                                                        appCoroutineScope,
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
                                                        } catch (e: CancellationException) {
                                                            statusText = "Status: Query canceled"
                                                        } catch (e: Exception) {
                                                            val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                                            statusText = "Status: Error in ${durationMs}ms: ${e.message ?: "Error"}"
                                                            ErrorHandler.showQueryError(
                                                                snackbarHostState,
                                                                appCoroutineScope,
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
                                                    if (pid == null) scratchSql = "" else sessionViewModel.updateQueryEditorSql(pid, "")
                                                },
                                                onSaveToFavorites = {
                                                    favoriteQueryDraft = sqlText
                                                    showSaveFavoriteDialog = true
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
                                                        id = idValue,
                                                    )

                                                    val r = try {
                                                        withContext(Dispatchers.IO) { QueryExecutor(driver).execute(stmt.sql, stmt.params) }
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        val msg = e.message ?: "Unknown error"
                                                        editError = msg
                                                        statusText = "Status: Update failed: $msg"
                                                        return@ResultGrid Result.failure(IllegalStateException(msg))
                                                    }

                                                    when (r) {
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
                                                },
                                            )
                                        }
                                    }
                                    NavigationTab.SchemaBrowser -> {
                                        val coroutineScope = rememberCoroutineScope()
                                        var schemaJob by remember(activeProfileId) { mutableStateOf<Job?>(null) }

                                        val ttlMs = 5 * 60 * 1000L
                                        val pid = activeProfileId
                                        val schemaState = pid?.let { sessionStates[it]?.schema } ?: SessionViewModel.SchemaUiState()
                                        val isLoadingSchema = schemaState.isLoading
                                        val schemaLoadedAtMs = schemaState.loadedAtMs
                                        val schemaNodes = schemaState.nodes
                                        val schemaDialogError = schemaState.dialogError
                                        val columnsCache = schemaState.columnsCache

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
                                                        children = cached,
                                                    )
                                                }

                                            val views = schema.views
                                                .sorted()
                                                .map { v ->
                                                    com.dbeagle.ui.SchemaTreeNode.View(
                                                        id = "view:$v",
                                                        label = v,
                                                    )
                                                }

                                            val indexes = schema.indexes
                                                .sorted()
                                                .map { idx ->
                                                    com.dbeagle.ui.SchemaTreeNode.Index(
                                                        id = "index:$idx",
                                                        label = idx,
                                                    )
                                                }

                                            return listOf(
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "section:tables",
                                                    label = "Tables",
                                                    children = tables,
                                                ),
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "section:views",
                                                    label = "Views",
                                                    children = views,
                                                ),
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "section:indexes",
                                                    label = "Indexes",
                                                    children = indexes,
                                                ),
                                            )
                                        }

                                        fun updateTableChildren(
                                            tableKey: String,
                                            newChildren: List<com.dbeagle.ui.SchemaTreeNode.Column>,
                                        ) {
                                            if (pid == null) return
                                            sessionViewModel.updateSchemaState(pid) { s ->
                                                s.copy(
                                                    nodes = s.nodes.map { node ->
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
                                            val driver = activeDriver
                                            if (driver == null) {
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
                                                statusText = "Status: Loading schema ($name)"
                                                sessionViewModel.updateSchemaState(pid) { it.copy(isLoading = true, dialogError = null) }
                                                try {
                                                    val schema = withContext(Dispatchers.IO) { driver.getSchema() }
                                                    val nodes = buildTree(schema)
                                                    val now = System.currentTimeMillis()
                                                    sessionViewModel.updateSchemaState(pid) {
                                                        it.copy(nodes = nodes, loadedAtMs = now, isLoading = false)
                                                    }
                                                    statusText = "Status: Schema loaded ($name)"
                                                } catch (e: CancellationException) {
                                                    statusText = "Status: Schema load canceled"
                                                } catch (e: Exception) {
                                                    statusText = "Status: Failed to load schema: ${e.message ?: "Error"}"
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
                                                text = { Text(schemaDialogError ?: "") },
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
                                                        enabled = hasConnection && !isLoadingSchema,
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
                                                com.dbeagle.ui.SchemaTree(
                                                    nodes = schemaNodes,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onNodeExpansionChanged = { node, expanded ->
                                                        if (!expanded) return@SchemaTree
                                                        if (node !is com.dbeagle.ui.SchemaTreeNode.Table) return@SchemaTree

                                                        val driver = activeDriver ?: return@SchemaTree

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
                                                            statusText = "Status: Loading columns ($name: $tableName)"
                                                            try {
                                                                val cols = withContext(Dispatchers.IO) { driver.getColumns(tableName) }
                                                                    .sortedBy { it.name }
                                                                    .map { c ->
                                                                        com.dbeagle.ui.SchemaTreeNode.Column(
                                                                            id = "col:$tableKey.${c.name}",
                                                                            label = c.name,
                                                                            type = c.type,
                                                                        )
                                                                    }
                                                                val now = System.currentTimeMillis()
                                                                sessionViewModel.updateSchemaState(activePid) { s ->
                                                                    s.copy(
                                                                        columnsCache = s.columnsCache + (tableKey to SessionViewModel.ColumnCacheEntry(now, cols)),
                                                                    )
                                                                }
                                                                updateTableChildren(tableKey, cols)
                                                                statusText = "Status: Columns loaded ($name: $tableName)"
                                                            } catch (e: CancellationException) {
                                                                statusText = "Status: Query canceled"
                                                            } catch (e: Exception) {
                                                                statusText = "Status: Failed to load columns: ${e.message ?: "Error"}"
                                                                sessionViewModel.updateSchemaState(activePid) {
                                                                    it.copy(dialogError = e.message ?: "Failed to load columns")
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onCopyName = { name -> println("App: Copy Name -> $name") },
                                                    onViewData = { name -> println("App: View Data -> $name") },
                                                )
                                            }
                                        }
                                    }
                                    NavigationTab.Favorites -> {
                                        com.dbeagle.ui.FavoritesScreen(
                                            repository = favoritesRepository,
                                            onLoadQuery = { query ->
                                                val pid = activeProfileId
                                                if (pid == null) scratchSql = query else sessionViewModel.updateQueryEditorSql(pid, query)
                                                selectedTab = NavigationTab.QueryEditor
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    NavigationTab.History -> {
                                        com.dbeagle.ui.HistoryScreen(
                                            repository = historyRepository,
                                            onLoadQuery = { query ->
                                                val pid = activeProfileId
                                                if (pid == null) scratchSql = query else sessionViewModel.updateQueryEditorSql(pid, query)
                                                selectedTab = NavigationTab.QueryEditor
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    NavigationTab.Settings -> {
                                        com.dbeagle.ui.SettingsScreen(
                                            onClose = { selectedTab = NavigationTab.Connections },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "${selectedTab.title} Content\n(Placeholder)",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onBackground,
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
