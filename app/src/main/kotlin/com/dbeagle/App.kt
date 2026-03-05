package com.dbeagle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dbeagle.crash.CrashReporter
import com.dbeagle.di.appModule
import com.dbeagle.navigation.NavigationTab
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.session.SessionViewModel
import com.dbeagle.theme.ThemeManager
import com.dbeagle.ui.AppBottomBar
import com.dbeagle.ui.ConnectionManagerScreen
import com.dbeagle.ui.FavoritesScreen
import com.dbeagle.ui.HistoryScreen
import com.dbeagle.ui.QueryEditorScreen
import com.dbeagle.ui.SchemaBrowserScreen
import com.dbeagle.ui.SettingsScreen
import com.dbeagle.ui.readMemoryStats
import com.dbeagle.ui.theme.DBEagleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

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

        val sessionViewModel: SessionViewModel = GlobalContext.get().get()
        val themeManager: ThemeManager = GlobalContext.get().get()
        val sessionOrder by sessionViewModel.sessionOrder.collectAsState()
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

        var scratchSql by remember { mutableStateOf(SessionViewModel.DEFAULT_SQL) }

        val appCoroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var triggerNewConnection by remember { mutableStateOf(false) }

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
                                        ConnectionManagerScreen(
                                            sessionViewModel = sessionViewModel,
                                            onStatusTextChanged = { statusText = it },
                                            triggerNewConnection = triggerNewConnection,
                                            onNewConnectionTriggered = { triggerNewConnection = false },
                                        )
                                    }
                                    NavigationTab.QueryEditor -> {
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
                                    NavigationTab.SchemaBrowser -> {
                                        SchemaBrowserScreen(
                                            sessionViewModel = sessionViewModel,
                                            activeProfileId = activeProfileId,
                                            sessionStates = sessionStates,
                                            activeDriver = activeDriver,
                                            activeProfileName = activeProfileName,
                                            onStatusTextChanged = { statusText = it },
                                            selectedTab = selectedTab,
                                        )
                                    }
                                    NavigationTab.Favorites -> {
                                        FavoritesScreen(
                                            onLoadQuery = { query ->
                                                val pid = activeProfileId
                                                if (pid == null) scratchSql = query else sessionViewModel.updateQueryEditorSql(pid, query)
                                                selectedTab = NavigationTab.QueryEditor
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    NavigationTab.History -> {
                                        HistoryScreen(
                                            onLoadQuery = { query ->
                                                val pid = activeProfileId
                                                if (pid == null) scratchSql = query else sessionViewModel.updateQueryEditorSql(pid, query)
                                                selectedTab = NavigationTab.QueryEditor
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    NavigationTab.Settings -> {
                                        SettingsScreen(
                                            onClose = { selectedTab = NavigationTab.Connections },
                                            modifier = Modifier.fillMaxSize(),
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
