package com.dbeagle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dbeagle.crash.CrashReporter
import com.dbeagle.di.appModule
import com.dbeagle.navigation.TabItem
import com.dbeagle.navigation.TabManager
import com.dbeagle.navigation.TabType
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.session.SessionViewModel
import com.dbeagle.settings.AppPreferencesRepository
import com.dbeagle.theme.ThemeManager
import com.dbeagle.ui.AppBottomBar
import com.dbeagle.ui.ConnectionDialog
import com.dbeagle.ui.ConnectionDot
import com.dbeagle.ui.ConnectionPanel
import com.dbeagle.ui.FavoritesScreen
import com.dbeagle.ui.HistoryScreen
import com.dbeagle.ui.LogViewerScreen
import com.dbeagle.ui.QueryEditorScreen
import com.dbeagle.ui.TableDataEditorScreen
import com.dbeagle.ui.WelcomeScreen
import com.dbeagle.ui.dialogs.MasterPasswordDialog
import com.dbeagle.ui.dialogs.SettingsDialog
import com.dbeagle.ui.readMemoryStats
import com.dbeagle.ui.theme.DBEagleTheme
import com.dbeagle.viewmodel.ConnectionListViewModel
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.History
import compose.icons.fontawesomeicons.solid.ListAlt
import compose.icons.fontawesomeicons.solid.Star
import compose.icons.fontawesomeicons.solid.Terminal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.slf4j.LoggerFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun main() {
    CrashReporter.install()
    val logger = LoggerFactory.getLogger("com.dbeagle.App")
    logger.info("DB Eagle starting...")

    startKoin {
        modules(appModule)
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        var statusText by remember { mutableStateOf("Status: Disconnected") }

        val sessionViewModel: SessionViewModel = koinInject()
        val themeManager: ThemeManager = koinInject()
        val tabManager = remember { TabManager() }
        val sessionStates by sessionViewModel.sessionStates.collectAsState()
        val activeProfileId by sessionViewModel.activeProfileId.collectAsState()
        val darkModeOverride by themeManager.darkModeOverride.collectAsState()
        val darkMode = darkModeOverride ?: isSystemInDarkTheme()

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

        val preferencesRepository: AppPreferencesRepository = koinInject()

        // Load persisted tabs on startup
        LaunchedEffect(Unit) {
            val savedTabs = preferencesRepository.openedTabsFlow.first()
            val savedSelectedId = preferencesRepository.selectedTabIdFlow.first()

            if (savedTabs.isNotEmpty()) {
                savedTabs.forEach { tabManager.addTab(it) }
                savedSelectedId?.let { tabManager.selectTab(it) }
            }
            // No default tab - show welcome screen when no tabs exist
        }

        // Save tabs on change (debounced)
        LaunchedEffect(tabManager.tabs.toList(), tabManager.selectedTabId) {
            delay(300) // debounce
            preferencesRepository.saveOpenedTabs(tabManager.tabs.toList(), tabManager.selectedTabId)
        }

        var scratchSql by remember { mutableStateOf(SessionViewModel.DEFAULT_SQL) }

        val appCoroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var triggerNewConnection by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var sidebarCollapsed by remember { mutableStateOf(false) }
        var masterPassword by remember { mutableStateOf<String?>(null) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "DB Eagle",
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyUp && (event.isCtrlPressed || event.isMetaPressed)) {
                    when (event.key) {
                        Key.N -> {
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
                            showSettingsDialog = true
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        ) {
            DBEagleTheme(darkTheme = darkMode) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("DB Eagle") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            actions = {
                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Text("New Query Editor")
                                            }
                                        }
                                    },
                                    delayMillis = 600,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomCenter,
                                        offset = DpOffset(0.dp, 8.dp),
                                    ),
                                ) {
                                    IconButton(onClick = {
                                        val existingTab = tabManager.tabs.find { it.type == TabType.QueryEditor }
                                        if (existingTab != null) {
                                            tabManager.selectTab(existingTab.id)
                                        } else {
                                            tabManager.addTab(
                                                TabItem(
                                                    type = TabType.QueryEditor,
                                                    title = "Query Editor",
                                                    connectionId = activeProfileId,
                                                ),
                                            )
                                        }
                                    }) {
                                        Icon(
                                            imageVector = FontAwesomeIcons.Solid.Terminal,
                                            contentDescription = "New Query Editor",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Text("Favorites")
                                            }
                                        }
                                    },
                                    delayMillis = 600,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomCenter,
                                        offset = DpOffset(0.dp, 8.dp),
                                    ),
                                ) {
                                    IconButton(onClick = {
                                        val existingTab = tabManager.tabs.find { it.type == TabType.Favorites }
                                        if (existingTab != null) {
                                            tabManager.selectTab(existingTab.id)
                                        } else {
                                            tabManager.addTab(
                                                TabItem(
                                                    type = TabType.Favorites,
                                                    title = "Favorites",
                                                ),
                                            )
                                        }
                                    }) {
                                        Icon(
                                            imageVector = FontAwesomeIcons.Solid.Star,
                                            contentDescription = "Favorites",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Text("History")
                                            }
                                        }
                                    },
                                    delayMillis = 600,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomCenter,
                                        offset = DpOffset(0.dp, 8.dp),
                                    ),
                                ) {
                                    IconButton(onClick = {
                                        val existingTab = tabManager.tabs.find { it.type == TabType.History }
                                        if (existingTab != null) {
                                            tabManager.selectTab(existingTab.id)
                                        } else {
                                            tabManager.addTab(
                                                TabItem(
                                                    type = TabType.History,
                                                    title = "History",
                                                ),
                                            )
                                        }
                                    }) {
                                        Icon(
                                            imageVector = FontAwesomeIcons.Solid.History,
                                            contentDescription = "History",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Text("Query Log")
                                            }
                                        }
                                    },
                                    delayMillis = 600,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomCenter,
                                        offset = DpOffset(0.dp, 8.dp),
                                    ),
                                ) {
                                    IconButton(onClick = {
                                        val existingTab = tabManager.tabs.find { it.type == TabType.QueryLog }
                                        if (existingTab != null) {
                                            tabManager.selectTab(existingTab.id)
                                        } else {
                                            tabManager.addTab(
                                                TabItem(
                                                    type = TabType.QueryLog,
                                                    title = "Query Log",
                                                ),
                                            )
                                        }
                                    }) {
                                        Icon(
                                            imageVector = FontAwesomeIcons.Solid.ListAlt,
                                            contentDescription = "Query Log",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Text("Settings")
                                            }
                                        }
                                    },
                                    delayMillis = 600,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomCenter,
                                        offset = DpOffset(0.dp, 8.dp),
                                    ),
                                ) {
                                    IconButton(onClick = { showSettingsDialog = true }) {
                                        Icon(
                                            imageVector = FontAwesomeIcons.Solid.Cog,
                                            contentDescription = "Settings",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        AppBottomBar(
                            statusText = statusText,
                            memoryStats = memoryStats,
                            poolStats = poolStats,
                            onStatusTextChanged = { statusText = it },
                        )
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                ) { innerPadding ->
                    if (showSettingsDialog) {
                        SettingsDialog(onDismiss = { showSettingsDialog = false })
                    }

                    if (masterPassword == null) {
                        MasterPasswordDialog(
                            onPasswordEntered = { masterPassword = it },
                        )
                    }

                    if (triggerNewConnection) {
                        ConnectionDialog(
                            initialProfile = null,
                            onDismiss = { triggerNewConnection = false },
                            onSave = { profile, plaintextPassword ->
                                appCoroutineScope.launch {
                                    // Non-Composable context: must use GlobalContext for parameterized injection
                                    val connectionListViewModel: ConnectionListViewModel =
                                        GlobalContext.get().get { parametersOf(masterPassword ?: "") }
                                    connectionListViewModel.saveProfile(profile, plaintextPassword)
                                }
                                triggerNewConnection = false
                            },
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        if (masterPassword != null) {
                            ConnectionPanel(
                                masterPassword = masterPassword!!,
                                sessionViewModel = sessionViewModel,
                                isCollapsed = sidebarCollapsed,
                                onCollapseToggle = { sidebarCollapsed = !sidebarCollapsed },
                                onNewConnection = { triggerNewConnection = true },
                                onStatusTextChanged = { statusText = it },
                                onOpenTableEditor = { connectionId, tableName ->
                                    tabManager.addTab(
                                        TabItem(
                                            type = TabType.TableEditor,
                                            title = tableName,
                                            connectionId = connectionId,
                                            tableName = tableName,
                                        ),
                                    )
                                },
                            )

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }

                        // Center content area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            // Only render tab row if tabs exist
                            if (tabManager.tabs.isNotEmpty()) {
                                val selectedNavTabIndex = tabManager.selectedTabId?.let { id ->
                                    tabManager.tabs.indexOfFirst { it.id == id }
                                }?.let { if (it >= 0) it else 0 } ?: 0

                                PrimaryScrollableTabRow(
                                    selectedTabIndex = selectedNavTabIndex,
                                    edgePadding = 8.dp,
                                ) {
                                    tabManager.tabs.forEach { tab ->
                                        Tab(
                                            selected = tabManager.selectedTabId == tab.id,
                                            onClick = {
                                                tab.connectionId?.let { sessionViewModel.setActiveProfile(it) }
                                                tabManager.selectTab(tab.id)
                                            },
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    ConnectionDot(
                                                        connectionId = tab.connectionId,
                                                        sessionStates = sessionStates,
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    val connectionName = tab.connectionId?.let {
                                                        sessionStates[it]?.profileName
                                                    }
                                                    val displayTitle = if (connectionName != null) {
                                                        "${tab.title} - $connectionName"
                                                    } else {
                                                        tab.title
                                                    }
                                                    Text(displayTitle)
                                                    Spacer(Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = { tabManager.closeTab(tab.id) },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Close tab")
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (tabManager.selectedTab?.type) {
                                    TabType.QueryEditor -> {
                                        QueryEditorScreen(
                                            sessionViewModel = sessionViewModel,
                                            activeProfileId = activeProfileId,
                                            activeSession = activeSession,
                                            activeDriver = activeDriver,
                                            activeProfileName = activeProfileName,
                                            scratchSql = scratchSql,
                                            onScratchSqlChange = { scratchSql = it },
                                            onStatusTextChanged = { statusText = it },
                                            snackbarHostState = snackbarHostState,
                                            sessionStates = sessionStates,
                                        )
                                    }
                                    TabType.Favorites -> {
                                        FavoritesScreen(
                                            onLoadQuery = { query ->
                                                val pid = activeProfileId
                                                if (pid == null) scratchSql = query else sessionViewModel.updateQueryEditorSql(pid, query)
                                                // Switch to QueryEditor tab
                                                val queryEditorTab = tabManager.tabs.find { it.type == TabType.QueryEditor }
                                                if (queryEditorTab != null) {
                                                    tabManager.selectTab(queryEditorTab.id)
                                                } else {
                                                    tabManager.addTab(
                                                        TabItem(
                                                            type = TabType.QueryEditor,
                                                            title = "Query Editor",
                                                            connectionId = activeProfileId,
                                                        ),
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    TabType.History -> {
                                        HistoryScreen(
                                            onLoadQuery = { query ->
                                                val pid = activeProfileId
                                                if (pid == null) scratchSql = query else sessionViewModel.updateQueryEditorSql(pid, query)
                                                // Switch to QueryEditor tab
                                                val queryEditorTab = tabManager.tabs.find { it.type == TabType.QueryEditor }
                                                if (queryEditorTab != null) {
                                                    tabManager.selectTab(queryEditorTab.id)
                                                } else {
                                                    tabManager.addTab(
                                                        TabItem(
                                                            type = TabType.QueryEditor,
                                                            title = "Query Editor",
                                                            connectionId = activeProfileId,
                                                        ),
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    TabType.QueryLog -> {
                                        LogViewerScreen(modifier = Modifier.fillMaxSize())
                                    }
                                    TabType.TableEditor -> {
                                        val connectionId = tabManager.selectedTab?.connectionId
                                        val tableName = tabManager.selectedTab?.tableName
                                        if (connectionId != null && tableName != null) {
                                            TableDataEditorScreen(
                                                sessionViewModel = sessionViewModel,
                                                connectionId = connectionId,
                                                tableName = tableName,
                                                onStatusTextChanged = { statusText = it },
                                                onCloseRequested = {
                                                    tabManager.selectedTab?.id?.let { id -> tabManager.closeTab(id) }
                                                },
                                                snackbarHostState = snackbarHostState,
                                            )
                                        } else {
                                            Text("Missing connection or table", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    null -> {
                                        WelcomeScreen(
                                            onNewQueryEditor = {
                                                tabManager.addTab(
                                                    TabItem(
                                                        type = TabType.QueryEditor,
                                                        title = "Query Editor",
                                                        connectionId = activeProfileId,
                                                    ),
                                                )
                                            },
                                            onOpenConnection = { triggerNewConnection = true },
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
