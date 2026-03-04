package com.dbeagle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDialog(
    initialProfile: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var type by remember { mutableStateOf<DatabaseType>(initialProfile?.type ?: DatabaseType.PostgreSQL) }
    var host by remember { mutableStateOf(initialProfile?.host ?: "localhost") }
    var port by remember { mutableStateOf(initialProfile?.port?.toString() ?: "5432") }
    var database by remember { mutableStateOf(initialProfile?.database ?: "") }
    var username by remember { mutableStateOf(initialProfile?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val isPostgreSQL = type is DatabaseType.PostgreSQL

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

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = if (isPostgreSQL) "PostgreSQL" else "SQLite",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("PostgreSQL") },
                            onClick = {
                                type = DatabaseType.PostgreSQL
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("SQLite") },
                            onClick = {
                                type = DatabaseType.SQLite
                                expanded = false
                            },
                        )
                    }
                }

                if (isPostgreSQL) {
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
                    label = { Text(if (isPostgreSQL) "Database" else "Database File Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isPostgreSQL) {
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
                    val pPort = port.toIntOrNull() ?: if (isPostgreSQL) 5432 else 0
                    val newProfile = ConnectionProfile(
                        id = initialProfile?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        type = type,
                        host = if (isPostgreSQL) host else "",
                        port = pPort,
                        database = database,
                        username = if (isPostgreSQL) username else "",
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
