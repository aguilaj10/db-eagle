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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    onCancelConnect: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
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
                        if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant,
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
                        text = { Text("Delete") },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        enabled = !isBusy,
                    )
                }
            }
        }
    }
}
