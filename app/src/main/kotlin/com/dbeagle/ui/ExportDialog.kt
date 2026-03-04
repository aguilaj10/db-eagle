package com.dbeagle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.awt.HeadlessException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ExportFormat {
    CSV, JSON, SQL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExportRequested: (ExportFormat, String) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var filePath by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text("Export Data") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Format:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ExportFormat.entries.forEach { format ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                enabled = !isExporting
                            )
                            Text(format.name, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        label = { Text("Export File Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !isExporting
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
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Browse")
                    }
                }

                if (isExporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Exporting data...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isExporting = true
                    coroutineScope.launch {
                        delay(1500) // Mock export delay
                        onExportRequested(selectedFormat, filePath)
                        isExporting = false
                        onDismiss()
                    }
                },
                enabled = filePath.isNotBlank() && !isExporting
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text("Cancel")
            }
        }
    )
}
