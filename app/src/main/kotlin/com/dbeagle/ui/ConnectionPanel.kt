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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.dbeagle.driver.DataDrivers
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.query.QueryExecutor
import com.dbeagle.session.SessionViewModel
import com.dbeagle.viewmodel.ConnectionListViewModel
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
                                                isExpanded = isExpanded,
                                                isLoading = schemaState?.isLoading ?: false,
                                                nodes = schemaState?.nodes ?: emptyList(),
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
    isExpanded: Boolean,
    isLoading: Boolean,
    nodes: List<SchemaTreeNode>,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onDoubleClickTable: (String) -> Unit,
) {
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
                    .height(300.dp)
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
                            onViewData = { tableName ->
                                onDoubleClickTable(tableName)
                            },
                        )
                    }
                }
            }
        }
    }
}
