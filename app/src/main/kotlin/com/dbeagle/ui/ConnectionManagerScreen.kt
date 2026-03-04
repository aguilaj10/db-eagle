package com.dbeagle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.driver.DatabaseDriverRegistry
import com.dbeagle.driver.DataDrivers
import com.dbeagle.error.ErrorHandler
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.profile.MasterPasswordProvider
import com.dbeagle.profile.PreferencesBackedConnectionProfileRepository
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.session.SessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
fun ConnectionManagerScreen(
    sessionViewModel: SessionViewModel,
    onStatusTextChanged: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
) {
    var masterPassword by remember { mutableStateOf<String?>(null) }
    
    if (masterPassword == null) {
        MasterPasswordDialog(
            onPasswordEntered = { masterPassword = it }
        )
    } else {
        ConnectionListScreen(
            masterPassword = masterPassword!!,
            sessionViewModel = sessionViewModel,
            onStatusTextChanged = onStatusTextChanged,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope,
        )
    }
}

@Composable
fun MasterPasswordDialog(onPasswordEntered: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Master Password Required") },
        text = {
            Column {
                Text("Please enter your master password to decrypt connection profiles.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasswordEntered(password) },
                enabled = password.isNotEmpty()
            ) {
                Text("Unlock")
            }
        }
    )
}

@Composable
fun ConnectionListScreen(
    masterPassword: String,
    sessionViewModel: SessionViewModel,
    onStatusTextChanged: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
) {
    val repository = remember(masterPassword) {
        PreferencesBackedConnectionProfileRepository(
            masterPasswordProvider = MasterPasswordProvider { masterPassword }
        )
    }

    var profiles by remember { mutableStateOf<List<ConnectionProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val connectedProfileIds by sessionViewModel.connectedProfileIds.collectAsState()
    val connectingProfileId by sessionViewModel.connectingProfileId.collectAsState()
    val activeProfileId by sessionViewModel.activeProfileId.collectAsState()

    var connectionErrorMessage by remember { mutableStateOf<String?>(null) }
    var retryConnection by remember { mutableStateOf<ConnectionProfile?>(null) }
    
    var showDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var connectingJob by remember { mutableStateOf<Job?>(null) }

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingProfile = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Connection")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val localStatus = if (connectedProfileIds.isEmpty()) "Disconnected" else "Connected (${connectedProfileIds.size})"
            Text(
                text = "Status: $localStatus",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (profiles.isEmpty()) {
                Text(
                    text = "No connections found. Click + to add one.",
                    modifier = Modifier.align(Alignment.Center)
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
                                    var driver: DatabaseDriver? = null
                                    try {
                                        val loaded = repository.load(profile.id)
                                            ?: throw IllegalStateException("Profile not found")

                                        val prototype = DatabaseDriverRegistry.getDriver(loaded.type)
                                            ?: throw IllegalStateException(
                                                "No driver registered for type: ${loaded.type}"
                                            )

                                        driver = try {
                                            prototype::class.java.getDeclaredConstructor().newInstance()
                                        } catch (e: Exception) {
                                            throw IllegalStateException(
                                                "Driver for type ${loaded.type} must have a no-arg constructor",
                                                e
                                            )
                                        }

                                        val password = loaded.encryptedPassword
                                        val configProfile = loaded.copy(
                                            options = loaded.options + ("password" to password)
                                        )

                                        onStatusTextChanged("Status: Connecting (${loaded.name})")

                                        withContext(Dispatchers.IO) {
                                            DatabaseConnectionPool.getConnection(loaded, password).use { _ -> }
                                            driver!!.connect(ConnectionConfig(profile = configProfile))
                                            try {
                                                driver!!.getSchema()
                                            } catch (schemaError: Exception) {
                                                try {
                                                    driver!!.disconnect()
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
                                            driver = driver!!
                                        )
                                        updateActiveConnection(loaded.id)

                                        onStatusTextChanged("Status: Connected (${loaded.name})")
                                    } catch (e: CancellationException) {
                                        try { driver?.disconnect() } catch (_: Exception) {}
                                        DatabaseConnectionPool.closePool(profile.id)
                                        updateStatus()
                                    } catch (e: Exception) {
                                        try {
                                            driver?.disconnect()
                                        } catch (_: Exception) {
                                        }
                                        DatabaseConnectionPool.closePool(profile.id)
                                        updateStatus()
                                        connectionErrorMessage = ErrorHandler.getConnectionErrorMessage(
                                            "Failed to connect: ${e.message}",
                                            e
                                        )
                                        retryConnection = profile
                                    } finally {
                                        sessionViewModel.setConnecting(null)
                                        if (connectingJob?.isActive != true) {
                                            connectingJob = null
                                        }
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
                            }
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
                            e
                        )
                        retryConnection = null
                        showDialog = false
                    }
                }
            }
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
                                var driver: DatabaseDriver? = null
                                try {
                                    val loaded = repository.load(profile.id)
                                        ?: throw IllegalStateException("Profile not found")

                                    val prototype = DatabaseDriverRegistry.getDriver(loaded.type)
                                        ?: throw IllegalStateException(
                                            "No driver registered for type: ${loaded.type}"
                                        )

                                    driver = try {
                                        prototype::class.java.getDeclaredConstructor().newInstance()
                                    } catch (e: Exception) {
                                        throw IllegalStateException(
                                            "Driver for type ${loaded.type} must have a no-arg constructor",
                                            e
                                        )
                                    }

                                    val password = loaded.encryptedPassword
                                    val configProfile = loaded.copy(
                                        options = loaded.options + ("password" to password)
                                    )

                                    onStatusTextChanged("Status: Connecting (${loaded.name})")

                                    withContext(Dispatchers.IO) {
                                        DatabaseConnectionPool.getConnection(loaded, password).use { _ -> }

                                        driver!!.connect(ConnectionConfig(profile = configProfile))

                                        try {
                                            driver!!.getSchema()
                                        } catch (schemaError: Exception) {
                                            try {
                                                driver!!.disconnect()
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
                                        driver = driver!!
                                    )
                                    updateActiveConnection(loaded.id)

                                    onStatusTextChanged("Status: Connected (${loaded.name})")
                                } catch (e: CancellationException) {
                                    try { driver?.disconnect() } catch (_: Exception) {}
                                    DatabaseConnectionPool.closePool(profile.id)
                                    updateStatus()
                                } catch (e: Exception) {
                                    try {
                                        driver?.disconnect()
                                    } catch (_: Exception) {
                                    }
                                    DatabaseConnectionPool.closePool(profile.id)
                                    updateStatus()
                                    connectionErrorMessage = ErrorHandler.getConnectionErrorMessage(
                                        "Failed to connect: ${e.message}",
                                        e
                                    )
                                    retryConnection = profile
                                } finally {
                                    sessionViewModel.setConnecting(null)
                                }
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
            }
        )
    }
}

@Composable
fun ConnectionRow(
    profile: ConnectionProfile,
    isConnected: Boolean,
    isConnecting: Boolean,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelConnect: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))

            // "Icon" - just a simple text for now representing the type
            Box(
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (profile.type is DatabaseType.PostgreSQL) "PG" else "SQ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = if (profile.type is DatabaseType.PostgreSQL) {
                    "${profile.username}@${profile.host}:${profile.port}/${profile.database}"
                } else {
                    profile.database
                }
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }

            Box {
                if (isConnecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        IconButton(onClick = onCancelConnect) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel Connection")
                        }
                    }
                } else {
                    IconButton(onClick = { expanded = true }, enabled = !isBusy) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (!isConnected) {
                        DropdownMenuItem(
                            text = { Text("Connect") },
                            onClick = {
                                expanded = false
                                onConnect()
                            },
                            enabled = !isBusy
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            onClick = {
                                expanded = false
                                onDisconnect()
                            },
                            enabled = !isBusy
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            expanded = false
                            onEdit()
                        },
                        enabled = !isBusy
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        enabled = !isBusy
                    )
                }
            }
        }
    }
}
