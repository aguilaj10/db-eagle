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
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.navigation.NavigationTab
import com.dbeagle.session.SessionViewModel
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

    val ttlMs = 5 * 60 * 1000L
    val pid = activeProfileId
    val schemaState = pid?.let { sessionStates[it]?.schema } ?: SessionViewModel.SchemaUiState()
    val isLoadingSchema = schemaState.isLoading
    val schemaNodes = schemaState.nodes
    val schemaDialogError = schemaState.dialogError
    val columnsCache = schemaState.columnsCache

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

        return listOf(
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
                    it.copy(nodes = nodes, loadedAtMs = now, isLoading = false)
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
                            val cols = withContext(Dispatchers.IO) { driver.getColumns(tableName) }
                                .sortedBy { it.name }
                                .map { c ->
                                    SchemaTreeNode.Column(
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
            )
        }
    }
}
