package com.dbeagle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.awt.HeadlessException

enum class ExportFormat {
    CSV,
    JSON,
    SQL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExportRequested: suspend (ExportFormat, String, (Int, Boolean) -> Unit) -> Unit,
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var filePath by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var exportedRowCount by remember { mutableStateOf(0) }
    var showProgress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text("Export Data") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Select Format:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ExportFormat.entries.forEach { format ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                enabled = !isExporting,
                            )
                            Text(format.name, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        label = { Text("Export File Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !isExporting,
                    )

                    Button(
                        onClick = {
                            try {
                                val dialog = FileDialog(null as Frame?, "Select Export File", FileDialog.SAVE)
                                dialog.file = "export.${selectedFormat.name.lowercase()}"
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val file = dialog.file
                                if (dir != null && file != null) {
                                    filePath = "$dir$file"
                                }
                            } catch (e: HeadlessException) {
                                println("HeadlessException: Cannot open native file dialog. Please enter path manually.")
                            } catch (e: Exception) {
                                println("Exception opening file dialog: ${e.message}")
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Browse")
                    }
                }

                if (isExporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showProgress) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Exported $exportedRowCount rows...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Exporting data...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isExporting = true
                    exportedRowCount = 0
                    showProgress = false
                    coroutineScope.launch {
                        onExportRequested(selectedFormat, filePath) { rowCount, isDone ->
                            exportedRowCount = rowCount
                            showProgress = rowCount > 1000
                            if (isDone) {
                                isExporting = false
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = filePath.isNotBlank() && !isExporting,
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting,
            ) {
                Text("Cancel")
            }
        },
    )
}
