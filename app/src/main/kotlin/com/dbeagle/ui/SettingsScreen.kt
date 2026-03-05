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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.settings.AppPreferences
import com.dbeagle.settings.AppSettings

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settings by remember { mutableStateOf(AppPreferences.load()) }
    var resultLimitInput by remember { mutableStateOf(settings.resultLimit.toString()) }
    var queryTimeoutInput by remember { mutableStateOf(settings.queryTimeoutSeconds.toString()) }
    var connectionTimeoutInput by remember { mutableStateOf(settings.connectionTimeoutSeconds.toString()) }
    var maxConnectionsInput by remember { mutableStateOf(settings.maxConnections.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var poolCount by remember { mutableStateOf(0) }
    var allPools by remember { mutableStateOf<Map<String, DatabaseConnectionPool.PoolStats>>(emptyMap()) }

    fun refreshPoolStats() {
        poolCount = DatabaseConnectionPool.getPoolCount()
        allPools = DatabaseConnectionPool.getAllPoolStats()
    }

    LaunchedEffect(Unit) {
        refreshPoolStats()
    }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Application Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedTextField(
            value = resultLimitInput,
            onValueChange = { resultLimitInput = it },
            label = { Text("Result Limit (max rows)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = queryTimeoutInput,
            onValueChange = { queryTimeoutInput = it },
            label = { Text("Query Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = connectionTimeoutInput,
            onValueChange = { connectionTimeoutInput = it },
            label = { Text("Connection Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = maxConnectionsInput,
            onValueChange = { maxConnectionsInput = it },
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
                    try {
                        val newSettings = AppSettings(
                            resultLimit = resultLimitInput.toInt(),
                            queryTimeoutSeconds = queryTimeoutInput.toInt(),
                            connectionTimeoutSeconds = connectionTimeoutInput.toInt(),
                            maxConnections = maxConnectionsInput.toInt(),
                        )
                        AppPreferences.save(newSettings)
                        settings = newSettings
                        errorMessage = null
                    } catch (e: NumberFormatException) {
                        errorMessage = "Invalid number format. Please enter valid integers."
                    } catch (e: IllegalArgumentException) {
                        errorMessage = e.message ?: "Invalid settings values. All values must be greater than 0."
                    }
                },
            ) {
                Text("Save")
            }

            Button(
                onClick = {
                    settings = AppSettings()
                    resultLimitInput = settings.resultLimit.toString()
                    queryTimeoutInput = settings.queryTimeoutSeconds.toString()
                    connectionTimeoutInput = settings.connectionTimeoutSeconds.toString()
                    maxConnectionsInput = settings.maxConnections.toString()
                    AppPreferences.save(settings)
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

        Button(onClick = { refreshPoolStats() }) {
            Text("Refresh Pool Stats")
        }

        Text(
            text = "Connection pools: $poolCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (allPools.isEmpty()) {
            Text(
                text = "No pools initialized.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allPools.toSortedMap().forEach { (id, s) ->
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
