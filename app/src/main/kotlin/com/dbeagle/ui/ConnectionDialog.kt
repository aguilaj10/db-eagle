package com.dbeagle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
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
    var expanded by remember { mutableStateOf(false) }

    val isPostgresSQL = type is DatabaseType.PostgreSQL

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
                        value = if (isPostgresSQL) "PostgresSQL" else "SQLite",
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
                            text = { Text("PostgresSQL") },
                            onClick = {
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("SQLite") },
                            onClick = {
                                expanded = false
                            },
                        )
                    }
                }

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
