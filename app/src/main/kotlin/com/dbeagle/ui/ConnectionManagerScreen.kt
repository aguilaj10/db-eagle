package com.dbeagle.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import com.dbeagle.session.SessionViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun ConnectionManagerScreen(
    sessionViewModel: SessionViewModel,
    onStatusTextChanged: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    triggerNewConnection: Boolean = false,
    onNewConnectionTriggered: () -> Unit = {},
) {
    var masterPassword by remember { mutableStateOf<String?>(null) }

    if (masterPassword == null) {
        MasterPasswordDialog(
            onPasswordEntered = { masterPassword = it },
        )
    } else {
        ConnectionListScreen(
            masterPassword = masterPassword!!,
            sessionViewModel = sessionViewModel,
            onStatusTextChanged = onStatusTextChanged,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope,
            triggerNewConnection = triggerNewConnection,
            onNewConnectionTriggered = onNewConnectionTriggered,
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasswordEntered(password) },
                enabled = password.isNotEmpty(),
            ) {
                Text("Unlock")
            }
        },
    )
}
