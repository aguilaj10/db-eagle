package com.dbeagle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dbeagle.di.appModule
import com.dbeagle.driver.DatabaseDriver
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
                                        var sqlQuery by remember { mutableStateOf("SELECT * FROM users;\n") }
                                        var columns by remember { mutableStateOf<List<String>>(emptyList()) }
                                        var rows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
                                        var isRunning by remember { mutableStateOf(false) }
                                        var queryError by remember { mutableStateOf<String?>(null) }
                                        val coroutineScope = rememberCoroutineScope()

                                        var showExportDialog by remember { mutableStateOf(false) }

                                        if (showExportDialog) {
                                            com.dbeagle.ui.ExportDialog(
                                                onDismiss = { showExportDialog = false },
                                                onExportRequested = { format, path ->
                                                    println("App: Export requested - Format: $format, Path: $path")
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

                                            com.dbeagle.ui.SQLEditor(
                                                sql = sqlQuery,
                                                onSqlChange = { sqlQuery = it },
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
                                                            when (val r = QueryExecutor(driver).execute(sqlQuery)) {
                                                                is QueryResult.Success -> {
                                                                    columns = r.columnNames
                                                                    rows = r.rows.map { rowMap ->
                                                                        r.columnNames.map { col -> rowMap[col] ?: "" }
                                                                    }
                                                                    val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                                                    statusText = "Status: ${rows.size} row(s) in ${durationMs}ms"
                                                                }
                                                                is QueryResult.Error -> {
                                                                    val durationMs = (System.nanoTime() - startNs) / 1_000_000
                                                                    statusText = "Status: Error in ${durationMs}ms: ${r.message}"
                                                                    queryError = r.message
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
                                                onClear = { sqlQuery = "" },
                                                onSaveToFavorites = { },
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
                                                modifier = Modifier.weight(0.6f)
                                            )
                                        }
                                    }
                                    NavigationTab.SchemaBrowser -> {
                                        val mockSchema = remember {
                                            listOf(
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "tables",
                                                    label = "Tables",
                                                    children = listOf(
                                                        com.dbeagle.ui.SchemaTreeNode.Table(
                                                            id = "table_users",
                                                            label = "users",
                                                            children = listOf(
                                                                com.dbeagle.ui.SchemaTreeNode.Column(id = "col_users_id", label = "id", type = "INT"),
                                                                com.dbeagle.ui.SchemaTreeNode.Column(id = "col_users_name", label = "name", type = "VARCHAR"),
                                                                com.dbeagle.ui.SchemaTreeNode.Column(id = "col_users_email", label = "email", type = "VARCHAR")
                                                            )
                                                        ),
                                                        com.dbeagle.ui.SchemaTreeNode.Table(
                                                            id = "table_orders",
                                                            label = "orders",
                                                            children = listOf(
                                                                com.dbeagle.ui.SchemaTreeNode.Column(id = "col_orders_id", label = "id", type = "INT"),
                                                                com.dbeagle.ui.SchemaTreeNode.Column(id = "col_orders_user_id", label = "user_id", type = "INT"),
                                                                com.dbeagle.ui.SchemaTreeNode.Column(id = "col_orders_total", label = "total", type = "DECIMAL")
                                                            )
                                                        )
                                                    )
                                                ),
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "views",
                                                    label = "Views",
                                                    children = listOf(
                                                        com.dbeagle.ui.SchemaTreeNode.View(id = "view_active_users", label = "active_users")
                                                    )
                                                ),
                                                com.dbeagle.ui.SchemaTreeNode.Section(
                                                    id = "indexes",
                                                    label = "Indexes",
                                                    children = listOf(
                                                        com.dbeagle.ui.SchemaTreeNode.Index(id = "idx_users_email", label = "users_email_idx")
                                                    )
                                                )
                                            )
                                        }

                                        com.dbeagle.ui.SchemaTree(
                                            nodes = mockSchema,
                                            onCopyName = { name -> println("App: Copy Name -> $name") },
                                            onViewData = { name -> println("App: View Data -> $name") }
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
