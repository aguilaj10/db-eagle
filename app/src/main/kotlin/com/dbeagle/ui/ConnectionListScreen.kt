package com.dbeagle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.driver.DataDrivers
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.driver.DatabaseDriverRegistry
import com.dbeagle.error.ErrorHandler
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.profile.PreferencesBackedConnectionProfileRepository
import com.dbeagle.session.SessionViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionListScreen(
    masterPassword: String,
    sessionViewModel: SessionViewModel,
    onStatusTextChanged: (String) -> Unit,
    coroutineScope: CoroutineScope,
    triggerNewConnection: Boolean = false,
    onNewConnectionTriggered: () -> Unit = {},
) {
    val repository = remember(masterPassword) {
        PreferencesBackedConnectionProfileRepository(
            masterPasswordProvider = { masterPassword },
        )
    }

    var profiles by remember { mutableStateOf<List<ConnectionProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val connectedProfileIds by sessionViewModel.connectedProfileIds.collectAsState()
    val connectingProfileId by sessionViewModel.connectingProfileId.collectAsState()

    var connectionErrorMessage by remember { mutableStateOf<String?>(null) }
    var retryConnection by remember { mutableStateOf<ConnectionProfile?>(null) }

    var showDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var connectingJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(triggerNewConnection) {
        if (triggerNewConnection) {
            editingProfile = null
            showDialog = true
            onNewConnectionTriggered()
        }
    }

    fun refreshProfiles() {
        coroutineScope.launch {
            try {
                isLoading = true
                error = null
                profiles = repository.loadAll().map { it.copy(encryptedPassword = "") }
            } catch (e: Exception) {
                error = "Failed to load profiles: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        DataDrivers.registerAll()
        refreshProfiles()
    }

    fun updateStatus() {
        val status = if (connectedProfileIds.isEmpty()) {
            "Status: Disconnected"
        } else {
            val connectedNames = profiles
                .filter { connectedProfileIds.contains(it.id) }
                .map { it.name }
            if (connectedNames.isNotEmpty()) {
                "Status: Connected (${connectedNames.joinToString(limit = 2, truncated = "…")})"
            } else {
                "Status: Connected (${connectedProfileIds.size})"
            }
        }
        onStatusTextChanged(status)
    }

    LaunchedEffect(connectedProfileIds, profiles) {
        updateStatus()
    }

    fun updateActiveConnection(profileId: String?) {
        sessionViewModel.setActiveProfile(profileId)
    }

    suspend fun connectToProfile(
        profile: ConnectionProfile,
        repository: PreferencesBackedConnectionProfileRepository,
        sessionViewModel: SessionViewModel,
        onStatusTextChanged: (String) -> Unit,
        updateActiveConnection: (String?) -> Unit,
        updateStatus: () -> Unit,
        onError: (String, ConnectionProfile) -> Unit,
    ) {
        var driver: DatabaseDriver? = null
        try {
            val loaded = repository.load(profile.id)
                ?: throw IllegalStateException("Profile not found")

            val prototype = DatabaseDriverRegistry.getDriver(loaded.type)
                ?: throw IllegalStateException(
                    "No driver registered for type: ${loaded.type}",
                )

            driver = try {
                prototype::class.java.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Driver for type ${loaded.type} must have a no-arg constructor",
                    e,
                )
            }

            val password = loaded.encryptedPassword
            val configProfile = loaded.copy(
                options = loaded.options + ("password" to password),
            )

            onStatusTextChanged("Status: Connecting (${loaded.name})")

            withContext(Dispatchers.IO) {
                DatabaseConnectionPool.getConnection(loaded, password).use { _ -> }

                driver!!.connect(ConnectionConfig(profile = configProfile))

                try {
                    driver.getSchema()
                } catch (schemaError: Exception) {
                    try {
                        driver.disconnect()
                    } catch (_: Exception) {
                    } finally {
                        DatabaseConnectionPool.closePool(loaded.id)
                    }
                    throw schemaError
                }
            }

            sessionViewModel.openSession(
                profileId = loaded.id,
                profileName = loaded.name,
                driver = driver!!,
            )
            updateActiveConnection(loaded.id)

            onStatusTextChanged("Status: Connected (${loaded.name})")
        } catch (_: CancellationException) {
            try {
                driver?.disconnect()
            } catch (_: Exception) {}
            DatabaseConnectionPool.closePool(profile.id)
            updateStatus()
        } catch (e: Exception) {
            try {
                driver?.disconnect()
            } catch (_: Exception) {
            }
            DatabaseConnectionPool.closePool(profile.id)
            updateStatus()
            onError(
                ErrorHandler.getConnectionErrorMessage(
                    "Failed to connect: ${e.message}",
                    e,
                ),
                profile,
            )
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingProfile = null
                showDialog = true
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Connection",
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val localStatus = if (connectedProfileIds.isEmpty()) "Disconnected" else "Connected (${connectedProfileIds.size})"
            Text(
                text = "Status: $localStatus",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (profiles.isEmpty()) {
                    Text(
                        text = "No connections found. Click + to add one.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(profiles) { profile ->
                            ConnectionRow(
                                profile = profile,
                                isConnected = connectedProfileIds.contains(profile.id),
                                isConnecting = connectingProfileId == profile.id,
                                isBusy = connectingProfileId != null,
                                onCancelConnect = {
                                    connectingJob?.cancel()
                                },
                                onConnect = {
                                    if (connectingProfileId != null) return@ConnectionRow
                                    connectingJob = coroutineScope.launch {
                                        sessionViewModel.setConnecting(profile.id)
                                        connectToProfile(
                                            profile = profile,
                                            repository = repository,
                                            sessionViewModel = sessionViewModel,
                                            onStatusTextChanged = onStatusTextChanged,
                                            updateActiveConnection = ::updateActiveConnection,
                                            updateStatus = ::updateStatus,
                                        ) { msg, p ->
                                            connectionErrorMessage = msg
                                            retryConnection = p
                                        }
                                        sessionViewModel.setConnecting(null)
                                        if (connectingJob?.isActive != true) {
                                            connectingJob = null
                                        }
                                    }
                                },
                                onDisconnect = {
                                    if (connectingProfileId != null) return@ConnectionRow
                                    coroutineScope.launch {
                                        sessionViewModel.setConnecting(profile.id)
                                        try {
                                            onStatusTextChanged("Status: Disconnecting (${profile.name})")
                                            sessionViewModel.closeSession(profile.id)
                                        } catch (_: Exception) {
                                        } finally {
                                            DatabaseConnectionPool.closePool(profile.id)
                                            updateStatus()
                                            sessionViewModel.setConnecting(null)
                                        }
                                    }
                                },
                                onEdit = {
                                    editingProfile = profile
                                    showDialog = true
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        repository.delete(profile.id)
                                        try {
                                            sessionViewModel.closeSession(profile.id)
                                        } catch (_: Exception) {
                                        }
                                        DatabaseConnectionPool.closePool(profile.id)

                                        refreshProfiles()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ConnectionDialog(
            initialProfile = editingProfile,
            onDismiss = { showDialog = false },
            onSave = { updatedProfile, plaintextPassword ->
                coroutineScope.launch {
                    try {
                        repository.save(updatedProfile, plaintextPassword)
                        showDialog = false
                        refreshProfiles()
                    } catch (e: Exception) {
                        connectionErrorMessage = ErrorHandler.getConnectionErrorMessage(
                            "Failed to save profile: ${e.message}",
                            e,
                        )
                        retryConnection = null
                        showDialog = false
                    }
                }
            },
        )
    }

    if (connectionErrorMessage != null) {
        AlertDialog(
            onDismissRequest = {
                connectionErrorMessage = null
                retryConnection = null
            },
            title = { Text("Connection Error") },
            text = { Text(connectionErrorMessage ?: "") },
            confirmButton = {
                if (retryConnection != null) {
                    TextButton(onClick = {
                        val profile = retryConnection
                        connectionErrorMessage = null
                        retryConnection = null
                        if (profile != null) {
                            connectingJob = coroutineScope.launch {
                                sessionViewModel.setConnecting(profile.id)
                                connectToProfile(
                                    profile = profile,
                                    repository = repository,
                                    sessionViewModel = sessionViewModel,
                                    onStatusTextChanged = onStatusTextChanged,
                                    updateActiveConnection = ::updateActiveConnection,
                                    updateStatus = ::updateStatus,
                                ) { msg, p ->
                                    connectionErrorMessage = msg
                                    retryConnection = p
                                }
                                sessionViewModel.setConnecting(null)
                            }
                        }
                    }) {
                        Text("Retry")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    connectionErrorMessage = null
                    retryConnection = null
                }) {
                    Text("Cancel")
                }
            },
        )
    }
}
