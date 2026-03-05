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
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.ValidationResult
import com.dbeagle.model.SequenceMetadata

@Composable
fun SequenceEditorDialog(
    existingSequence: SequenceMetadata?,
    onDismiss: () -> Unit,
    onSave: (SequenceMetadata) -> Unit,
) {
    var name by remember { mutableStateOf(existingSequence?.name ?: "") }
    var startValue by remember { mutableStateOf(existingSequence?.startValue?.toString() ?: "1") }
    var increment by remember { mutableStateOf(existingSequence?.increment?.toString() ?: "1") }
    var minValue by remember { mutableStateOf(existingSequence?.minValue?.toString() ?: "") }
    var maxValue by remember { mutableStateOf(existingSequence?.maxValue?.toString() ?: "") }
    var cycle by remember { mutableStateOf(existingSequence?.cycle ?: false) }

    val isOwnedSequence = existingSequence?.ownedByTable != null
    val isCreateMode = existingSequence == null

    val nameValidation = if (name.isNotBlank()) {
        DDLValidator.validateIdentifier(name)
    } else {
        ValidationResult.Invalid(listOf("Name is required"))
    }
    val isNameValid = nameValidation is ValidationResult.Valid

    val validationErrors = when (nameValidation) {
        is ValidationResult.Invalid -> nameValidation.errors
        ValidationResult.Valid -> emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isOwnedSequence -> "View Sequence (Owned by ${existingSequence.ownedByTable}.${existingSequence.ownedByColumn})"
                    isCreateMode -> "Create Sequence"
                    else -> "Edit Sequence"
                },
            )
        },
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
                    enabled = !isOwnedSequence,
                    isError = !isNameValid && name.isNotBlank(),
                    supportingText = {
                        if (!isNameValid && validationErrors.isNotEmpty()) {
                            Text(validationErrors.first())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startValue,
                        onValueChange = { startValue = it },
                        label = { Text("Start Value") },
                        singleLine = true,
                        enabled = !isOwnedSequence,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = increment,
                        onValueChange = { increment = it },
                        label = { Text("Increment") },
                        singleLine = true,
                        enabled = !isOwnedSequence,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minValue,
                        onValueChange = { minValue = it },
                        label = { Text("Min Value (optional)") },
                        singleLine = true,
                        enabled = !isOwnedSequence,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxValue,
                        onValueChange = { maxValue = it },
                        label = { Text("Max Value (optional)") },
                        singleLine = true,
                        enabled = !isOwnedSequence,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = cycle,
                        onCheckedChange = { cycle = it },
                        enabled = !isOwnedSequence,
                    )
                    Text("Cycle")
                }

                if (isOwnedSequence) {
                    Text(
                        text = "This sequence is owned by a SERIAL column and cannot be edited.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (!isOwnedSequence) {
                Button(
                    onClick = {
                        val sequence = SequenceMetadata(
                            name = name,
                            schema = existingSequence?.schema ?: "public",
                            startValue = startValue.toLongOrNull() ?: 1L,
                            increment = increment.toLongOrNull() ?: 1L,
                            minValue = minValue.toLongOrNull() ?: 1L,
                            maxValue = maxValue.toLongOrNull() ?: Long.MAX_VALUE,
                            cycle = cycle,
                            ownedByTable = existingSequence?.ownedByTable,
                            ownedByColumn = existingSequence?.ownedByColumn,
                        )
                        onSave(sequence)
                    },
                    enabled = isNameValid,
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isOwnedSequence) "Close" else "Cancel")
            }
        },
    )
}
