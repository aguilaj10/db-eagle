package com.dbeagle.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.ddl.DDLDialect
import com.dbeagle.viewmodel.ViewEditorViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun ViewEditorDialog(
    dialect: DDLDialect,
    onDismiss: () -> Unit,
    onSave: (ddl: String) -> Unit,
) {
    val viewModel: ViewEditorViewModel = koinInject { parametersOf(dialect) }
    val uiState by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create View") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.viewName,
                    onValueChange = viewModel::updateViewName,
                    label = { Text("View Name") },
                    singleLine = true,
                    isError = !uiState.isNameValid && uiState.viewName.isNotBlank(),
                    supportingText = {
                        if (!uiState.isNameValid && uiState.validationErrors.isNotEmpty()) {
                            Text(uiState.validationErrors.first())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.schema,
                    onValueChange = viewModel::updateSchema,
                    label = { Text("Schema (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.selectQuery,
                    onValueChange = viewModel::updateSelectQuery,
                    label = { Text("SELECT Query") },
                    placeholder = { Text("SELECT column1, column2 FROM table1 WHERE ...") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = uiState.orReplace,
                        onCheckedChange = viewModel::toggleOrReplace,
                    )
                    Text("OR REPLACE")
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.togglePreview(true) },
                    enabled = uiState.isFormValid,
                ) {
                    Text("Preview")
                }
                Button(
                    onClick = { onSave(uiState.generatedDdl) },
                    enabled = uiState.isFormValid,
                ) {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (uiState.showPreview) {
        DDLPreviewDialog(
            ddlSql = uiState.generatedDdl,
            isDestructive = uiState.orReplace,
            onDismiss = { viewModel.togglePreview(false) },
            onExecute = {
                onSave(uiState.generatedDdl)
                viewModel.togglePreview(false)
            },
        )
    }
}
