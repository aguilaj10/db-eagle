package com.dbeagle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

@Composable
fun ConnectionRow(
    profile: ConnectionProfile,
    isConnected: Boolean,
    isConnecting: Boolean,
    isBusy: Boolean,
    connectionError: String? = null,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelConnect: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val connectionState = when {
        connectionError != null -> ConnectionState.ERROR
        isConnecting -> ConnectionState.CONNECTING
        isConnected -> ConnectionState.CONNECTED
        else -> ConnectionState.DISCONNECTED
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                ConnectionState.CONNECTING -> Color(0xFFFFC107)
                                ConnectionState.ERROR -> Color(0xFFF44336)
                                ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E)
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (profile.type is DatabaseType.PostgreSQL) "PG" else "SQ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
                                strokeWidth = 2.dp,
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
                        onDismissRequest = { expanded = false },
                    ) {
                        if (!isConnected) {
                            DropdownMenuItem(
                                text = { Text("Connect") },
                                onClick = {
                                    expanded = false
                                    onConnect()
                                },
                                enabled = !isBusy,
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Disconnect") },
                                onClick = {
                                    expanded = false
                                    onDisconnect()
                                },
                                enabled = !isBusy,
                            )
                            DropdownMenuItem(
                                text = { Text("Reconnect") },
                                onClick = {
                                    expanded = false
                                    onReconnect()
                                },
                                enabled = !isBusy,
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                expanded = false
                                onEdit()
                            },
                            enabled = !isBusy,
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                expanded = false
                                showDeleteConfirmation = true
                            },
                            enabled = !isBusy,
                        )
                    }
                }
            }

            if (connectionError != null) {
                Text(
                    text = connectionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Remove Connection") },
            text = { Text("Are you sure you want to remove \"${profile.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
