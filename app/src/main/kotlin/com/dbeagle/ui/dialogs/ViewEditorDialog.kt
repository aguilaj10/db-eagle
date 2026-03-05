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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.ddl.DDLDialect
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.ValidationResult
import com.dbeagle.ddl.ViewDDLBuilder

@Composable
fun ViewEditorDialog(
    dialect: DDLDialect,
    onDismiss: () -> Unit,
    onSave: (ddl: String) -> Unit,
) {
    var viewName by remember { mutableStateOf("") }
    var schema by remember { mutableStateOf("") }
    var selectQuery by remember { mutableStateOf("") }
    var orReplace by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    val nameValidation = if (viewName.isNotBlank()) {
        DDLValidator.validateIdentifier(viewName)
    } else {
        ValidationResult.Invalid(listOf("View name is required"))
    }
    val isNameValid = nameValidation is ValidationResult.Valid

    val isQueryValid = selectQuery.isNotBlank()

    val validationErrors = when (nameValidation) {
        is ValidationResult.Invalid -> nameValidation.errors
        ValidationResult.Valid -> emptyList()
    }

    val isFormValid = isNameValid && isQueryValid

    val generatedDdl = if (isFormValid) {
        ViewDDLBuilder.buildCreateView(
            dialect = dialect,
            name = viewName,
            selectQuery = selectQuery,
            schema = schema.ifBlank { null },
            orReplace = orReplace,
        )
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create View") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = viewName,
                    onValueChange = { viewName = it },
                    label = { Text("View Name") },
                    singleLine = true,
                    isError = !isNameValid && viewName.isNotBlank(),
                    supportingText = {
                        if (!isNameValid && validationErrors.isNotEmpty()) {
                            Text(validationErrors.first())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = schema,
                    onValueChange = { schema = it },
                    label = { Text("Schema (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = selectQuery,
                    onValueChange = { selectQuery = it },
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
                        checked = orReplace,
                        onCheckedChange = { orReplace = it },
                    )
                    Text("OR REPLACE")
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { showPreview = true },
                    enabled = isFormValid,
                ) {
                    Text("Preview")
                }
                Button(
                    onClick = { onSave(generatedDdl) },
                    enabled = isFormValid,
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

    if (showPreview) {
        DDLPreviewDialog(
            ddlSql = generatedDdl,
            isDestructive = orReplace,
            onDismiss = { showPreview = false },
            onExecute = {
                onSave(generatedDdl)
                showPreview = false
            },
        )
    }
}
