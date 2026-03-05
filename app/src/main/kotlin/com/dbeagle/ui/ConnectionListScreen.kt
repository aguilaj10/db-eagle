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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.driver.DataDrivers
import com.dbeagle.session.SessionViewModel
import com.dbeagle.viewmodel.ConnectionListViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

@Composable
fun ConnectionListScreen(
    masterPassword: String,
    sessionViewModel: SessionViewModel,
    onStatusTextChanged: (String) -> Unit,
    triggerNewConnection: Boolean = false,
    onNewConnectionTriggered: () -> Unit = {},
) {
    val viewModel: ConnectionListViewModel = remember(masterPassword) {
        GlobalContext.get().get { parametersOf(masterPassword) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val connectedProfileIds by sessionViewModel.connectedProfileIds.collectAsState()
    val connectingProfileId by sessionViewModel.connectingProfileId.collectAsState()

    LaunchedEffect(triggerNewConnection) {
        if (triggerNewConnection) {
            viewModel.showDialog(editingProfile = null)
            onNewConnectionTriggered()
        }
    }

    LaunchedEffect(Unit) {
        DataDrivers.registerAll()
        viewModel.refreshProfiles()
    }

    fun updateStatus() {
        val status = if (connectedProfileIds.isEmpty()) {
            "Status: Disconnected"
        } else {
            val connectedNames = uiState.profiles
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

    LaunchedEffect(connectedProfileIds, uiState.profiles) {
        updateStatus()
    }

    fun updateActiveConnection(profileId: String?) {
        sessionViewModel.setActiveProfile(profileId)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.showDialog(editingProfile = null)
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
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.error != null) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (uiState.profiles.isEmpty()) {
                    Text(
                        text = "No connections found. Click + to add one.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.profiles) { profile ->
                            ConnectionRow(
                                profile = profile,
                                isConnected = connectedProfileIds.contains(profile.id),
                                isConnecting = connectingProfileId == profile.id,
                                isBusy = connectingProfileId != null,
                                onCancelConnect = {
                                    viewModel.cancelConnect()
                                },
                                onConnect = {
                                    if (connectingProfileId != null) return@ConnectionRow
                                    viewModel.connect(
                                        profile = profile,
                                        onStatusTextChanged = onStatusTextChanged,
                                        onSessionOpen = { profileId, profileName, driver ->
                                            sessionViewModel.openSession(profileId, profileName, driver)
                                            updateActiveConnection(profileId)
                                        },
                                        onSetConnecting = { profileId ->
                                            sessionViewModel.setConnecting(profileId)
                                        },
                                    )
                                },
                                onDisconnect = {
                                    if (connectingProfileId != null) return@ConnectionRow
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
                                        onUpdateStatus = ::updateStatus,
                                    )
                                },
                                onEdit = {
                                    viewModel.showDialog(editingProfile = profile)
                                },
                                onDelete = {
                                    viewModel.deleteProfile(
                                        profileId = profile.id,
                                        onSessionClose = { profileId ->
                                            sessionViewModel.closeSession(profileId)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showDialog) {
        ConnectionDialog(
            initialProfile = uiState.editingProfile,
            onDismiss = { viewModel.hideDialog() },
            onSave = { updatedProfile, plaintextPassword ->
                viewModel.saveProfile(updatedProfile, plaintextPassword)
            },
        )
    }

    if (uiState.connectionError != null) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearConnectionError()
            },
            title = { Text("Connection Error") },
            text = { Text(uiState.connectionError ?: "") },
            confirmButton = {
                if (uiState.connectionErrorProfile != null) {
                    TextButton(onClick = {
                        viewModel.retryConnection(
                            onStatusTextChanged = onStatusTextChanged,
                            onSessionOpen = { profileId, profileName, driver ->
                                sessionViewModel.openSession(profileId, profileName, driver)
                                updateActiveConnection(profileId)
                            },
                            onSetConnecting = { profileId ->
                                sessionViewModel.setConnecting(profileId)
                            },
                        )
                    }) {
                        Text("Retry")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearConnectionError()
                }) {
                    Text("Cancel")
                }
            },
        )
    }
}
