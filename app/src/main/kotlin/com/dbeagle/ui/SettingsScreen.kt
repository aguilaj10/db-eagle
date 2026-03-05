package com.dbeagle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.viewmodel.SettingsViewModel
import org.koin.core.context.GlobalContext

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { GlobalContext.get().get<SettingsViewModel>() }
    val uiState by viewModel.uiState.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshPoolStats()
    }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Application Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Dark Mode",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = darkMode,
                onCheckedChange = { viewModel.setDarkMode(it) },
            )
        }

        OutlinedTextField(
            value = uiState.resultLimitInput,
            onValueChange = { viewModel.updateResultLimit(it) },
            label = { Text("Result Limit (max rows)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = uiState.queryTimeoutInput,
            onValueChange = { viewModel.updateQueryTimeout(it) },
            label = { Text("Query Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = uiState.connectionTimeoutInput,
            onValueChange = { viewModel.updateConnectionTimeout(it) },
            label = { Text("Connection Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = uiState.maxConnectionsInput,
            onValueChange = { viewModel.updateMaxConnections(it) },
            label = { Text("Max Connections") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    viewModel.saveSettings()
                },
            ) {
                Text("Save")
            }

            Button(
                onClick = {
                    viewModel.resetToDefaults()
                },
            ) {
                Text("Reset to Defaults")
            }

            Button(
                onClick = onClose,
            ) {
                Text("Close")
            }
        }

        HorizontalDivider()

        Text(
            text = "Debug",
            style = MaterialTheme.typography.titleMedium,
        )

        Button(onClick = { viewModel.refreshPoolStats() }) {
            Text("Refresh Pool Stats")
        }

        Text(
            text = "Connection pools: ${uiState.poolCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (uiState.allPools.isEmpty()) {
            Text(
                text = "No pools initialized.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.allPools.toSortedMap().forEach { (id, s) ->
                    Text(
                        text = "Pool ${id.take(8)}: active=${s.active}, idle=${s.idle}, total=${s.total}, waiting=${s.waiting}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
