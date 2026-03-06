package com.dbeagle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dbeagle.model.QueryHistoryEntry
import com.dbeagle.viewmodel.HistoryViewModel
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onLoadQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = GlobalContext.get().get<HistoryViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearDialog() },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear all query history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${uiState.entries.size} ${if (uiState.entries.size == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { viewModel.showClearDialog() },
                enabled = uiState.entries.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Clear History", style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider()

        if (uiState.entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No query history yet.\nExecute queries in Query Editor to see them here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(uiState.entries) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        onClick = { onLoadQuery(entry.query) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: QueryHistoryEntry,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val timestampText = dateFormat.format(Date(entry.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${entry.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.connectionProfileId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.query,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
