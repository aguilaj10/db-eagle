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
import com.dbeagle.model.SequenceMetadata
import com.dbeagle.viewmodel.SequenceEditorViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun SequenceEditorDialog(
    existingSequence: SequenceMetadata?,
    onDismiss: () -> Unit,
    onSave: (SequenceMetadata) -> Unit,
) {
    val viewModel: SequenceEditorViewModel = koinInject { parametersOf(existingSequence) }
    val uiState by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    uiState.isOwnedSequence -> "View Sequence (Owned by ${existingSequence?.ownedByTable}.${existingSequence?.ownedByColumn})"
                    uiState.isCreateMode -> "Create Sequence"
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
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !uiState.isOwnedSequence,
                    isError = !uiState.isNameValid && uiState.name.isNotBlank(),
                    supportingText = {
                        if (!uiState.isNameValid && uiState.validationErrors.isNotEmpty()) {
                            Text(uiState.validationErrors.first())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.startValue,
                        onValueChange = { viewModel.updateStartValue(it) },
                        label = { Text("Start Value") },
                        singleLine = true,
                        enabled = !uiState.isOwnedSequence,
                        isError = uiState.validationErrors.contains("Invalid start value"),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.increment,
                        onValueChange = { viewModel.updateIncrement(it) },
                        label = { Text("Increment") },
                        singleLine = true,
                        enabled = !uiState.isOwnedSequence,
                        isError = uiState.validationErrors.contains("Invalid increment value"),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.minValue,
                        onValueChange = { viewModel.updateMinValue(it) },
                        label = { Text("Min Value (optional)") },
                        singleLine = true,
                        enabled = !uiState.isOwnedSequence,
                        isError = uiState.validationErrors.contains("Invalid min value"),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.maxValue,
                        onValueChange = { viewModel.updateMaxValue(it) },
                        label = { Text("Max Value (optional)") },
                        singleLine = true,
                        enabled = !uiState.isOwnedSequence,
                        isError = uiState.validationErrors.contains("Invalid max value"),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = uiState.cycle,
                        onCheckedChange = { viewModel.toggleCycle() },
                        enabled = !uiState.isOwnedSequence,
                    )
                    Text("Cycle")
                }

                if (uiState.isOwnedSequence) {
                    Text(
                        text = "This sequence is owned by a SERIAL column and cannot be edited.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (!uiState.isOwnedSequence) {
                Button(
                    onClick = {
                        val sequence = viewModel.buildSequenceMetadata(
                            schema = existingSequence?.schema ?: "public",
                        )
                        onSave(sequence)
                    },
                    enabled = uiState.isFormValid,
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (uiState.isOwnedSequence) "Close" else "Cancel")
            }
        },
    )
}
