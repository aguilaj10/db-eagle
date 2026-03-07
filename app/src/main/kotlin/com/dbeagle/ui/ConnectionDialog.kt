package com.dbeagle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.ui.components.ReadonlyDropdownField
import java.util.UUID

@Composable
fun ConnectionDialog(
    initialProfile: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var type by remember { mutableStateOf(initialProfile?.type ?: DatabaseType.PostgreSQL) }
    var host by remember { mutableStateOf(initialProfile?.host ?: "localhost") }
    var port by remember { mutableStateOf(initialProfile?.port?.toString() ?: "5432") }
    var database by remember { mutableStateOf(initialProfile?.database ?: "") }
    var username by remember { mutableStateOf(initialProfile?.username ?: "") }
    var password by remember { mutableStateOf("") }

    val isPostgresSQL = type is DatabaseType.PostgreSQL
    val databaseTypes = listOf(DatabaseType.PostgreSQL, DatabaseType.SQLite)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProfile == null) "New Connection" else "Edit Connection") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ReadonlyDropdownField(
                    label = "Type",
                    value = type,
                    options = databaseTypes,
                    onSelect = { type = it },
                    valueText = { when(it) {
                        is DatabaseType.PostgreSQL -> "PostgresSQL"
                        is DatabaseType.SQLite -> "SQLite"
                        is DatabaseType.Oracle -> "Oracle"
                    }},
                    modifier = Modifier.fillMaxWidth()
                )

                if (isPostgresSQL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host") },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                OutlinedTextField(
                    value = database,
                    onValueChange = { database = it },
                    label = { Text(if (isPostgresSQL) "Database" else "Database File Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isPostgresSQL) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (initialProfile == null) "Password" else "Password (re-enter to save)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pPort = port.toIntOrNull() ?: if (isPostgresSQL) 5432 else 0
                    val newProfile = ConnectionProfile(
                        id = initialProfile?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        type = type,
                        host = if (isPostgresSQL) host else "",
                        port = pPort,
                        database = database,
                        username = if (isPostgresSQL) username else "",
                        encryptedPassword = initialProfile?.encryptedPassword ?: "",
                    )
                    onSave(newProfile, password)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
