package com.dbeagle.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dbeagle.logging.QueryLogEntry
import com.dbeagle.logging.QueryStatus
import com.dbeagle.viewmodel.LogFilter
import com.dbeagle.viewmodel.LogViewerViewModel
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogViewerScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { GlobalContext.get().get<LogViewerViewModel>() }
    val uiState by viewModel.uiState.collectAsState()
    val filteredLogs = viewModel.filteredLogs

    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearDialog() },
            title = { Text("Clear Logs") },
            text = { Text("Are you sure you want to clear all query logs? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
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
                text = "${filteredLogs.size} ${if (filteredLogs.size == 1) "log" else "logs"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.refreshLogs() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Refresh", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = { viewModel.showClearDialog() },
                    enabled = uiState.logs.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Clear Logs", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = uiState.filter == LogFilter.ALL,
                onClick = { viewModel.setFilter(LogFilter.ALL) },
                label = { Text("All") },
            )
            FilterChip(
                selected = uiState.filter == LogFilter.SUCCESS,
                onClick = { viewModel.setFilter(LogFilter.SUCCESS) },
                label = { Text("Success") },
            )
            FilterChip(
                selected = uiState.filter == LogFilter.ERROR,
                onClick = { viewModel.setFilter(LogFilter.ERROR) },
                label = { Text("Error") },
            )
        }

        HorizontalDivider()

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (uiState.logs.isEmpty()) {
                        "No query logs yet.\nExecute queries in Query Editor to see logs here."
                    } else {
                        "No logs match the selected filter."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val colorScheme = MaterialTheme.colorScheme
            val logText = formatLogsAsText(filteredLogs, colorScheme)

            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.surfaceContainer)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatLogsAsText(logs: List<QueryLogEntry>, colorScheme: androidx.compose.material3.ColorScheme): AnnotatedString {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val separator = "-".repeat(80)

    return buildAnnotatedString {
        logs.forEachIndexed { index, entry ->
            val timestampText = dateFormat.format(Date(entry.timestamp))
            pushStyle(SpanStyle(color = colorScheme.onSurfaceVariant))
            append("[$timestampText] ")

            append("(${entry.durationMs}ms) ")

            when (entry.status) {
                QueryStatus.SUCCESS -> {
                    pushStyle(SpanStyle(color = colorScheme.primary))
                    append("[SUCCESS]")
                    pop()

                    if (entry.rowCount != null) {
                        pushStyle(SpanStyle(color = colorScheme.secondary))
                        append(" [${entry.rowCount} rows]")
                        pop()
                    }
                }
                QueryStatus.ERROR -> {
                    pushStyle(SpanStyle(color = colorScheme.error))
                    append("[ERROR]")
                    pop()
                }
            }

            appendLine()

            appendLine(entry.sql)

            if (entry.status == QueryStatus.ERROR && entry.errorMessage != null) {
                pushStyle(SpanStyle(color = colorScheme.error))
                appendLine("Error: ${entry.errorMessage}")
                pop()
            }

            if (index < logs.size - 1) {
                appendLine(separator)
                appendLine()
            }
        }
    }
}
