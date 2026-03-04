package com.dbeagle.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
fun ResultGrid(
    columns: List<String>,
    rows: List<List<String>>,
    pageSize: Int = 25,
    modifier: Modifier = Modifier,
    onCellCommit: suspend (rowIndex: Int, columnName: String, newValue: String, rowSnapshot: List<String>) -> Result<Unit>,
) {
    var localRows by remember(rows) { mutableStateOf(rows.map { it.toMutableList() }.toMutableList()) }
    var baselineRows by remember(rows) { mutableStateOf(rows.map { it.toList() }) }
    var currentPage by remember(rows) { mutableStateOf(0) }

    val totalPages = max(1, (localRows.size + pageSize - 1) / pageSize)
    currentPage = min(currentPage, totalPages - 1)

    val horizontalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var gridError by remember { mutableStateOf<String?>(null) }
    if (gridError != null) {
        AlertDialog(
            onDismissRequest = { gridError = null },
            title = { Text("Edit Error") },
            text = { Text(gridError ?: "") },
            confirmButton = {
                TextButton(onClick = { gridError = null }) {
                    Text("OK")
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Total rows: ${localRows.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { currentPage = max(0, currentPage - 1) },
                    enabled = currentPage > 0,
                ) {
                    Text("Prev")
                }
                Text(
                    " Page ${currentPage + 1} of $totalPages ",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { currentPage = min(totalPages - 1, currentPage + 1) },
                    enabled = currentPage < totalPages - 1,
                ) {
                    Text("Next")
                }
            }
        }

        HorizontalDivider()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    columns.forEach { col ->
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(8.dp),
                        ) {
                            Text(
                                text = col,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val startIdx = currentPage * pageSize
                val endIdx = min(startIdx + pageSize, localRows.size)
                val visibleRows = if (localRows.isEmpty()) emptyList() else localRows.subList(startIdx, endIdx)

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(
                        items = visibleRows,
                        key = { rIdx, _ -> startIdx + rIdx },
                    ) { rIdx, row ->
                        val actualRowIdx = startIdx + rIdx
                        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            row.forEachIndexed { cIdx, cellValue ->
                                val colName = columns.getOrNull(cIdx) ?: ""
                                val baseline = baselineRows.getOrNull(actualRowIdx)?.getOrNull(cIdx)
                                val isDirty = baseline != cellValue

                                EditableCell(
                                    value = cellValue,
                                    isDirty = isDirty,
                                    onValueChange = { newValue ->
                                        val original = baselineRows.getOrNull(actualRowIdx)?.getOrNull(cIdx) ?: ""
                                        if (colName.equals("id", ignoreCase = true)) {
                                            gridError = "Editing the id column is not supported."
                                            localRows = localRows.toMutableList().apply {
                                                this[actualRowIdx] = this[actualRowIdx].toMutableList().apply {
                                                    this[cIdx] = original
                                                }
                                            }
                                            return@EditableCell
                                        }

                                        localRows = localRows.toMutableList().apply {
                                            this[actualRowIdx] = this[actualRowIdx].toMutableList().apply {
                                                this[cIdx] = newValue
                                            }
                                        }

                                        if (newValue == original) return@EditableCell

                                        val rowSnapshot = localRows.getOrNull(actualRowIdx)?.toList() ?: return@EditableCell
                                        coroutineScope.launch {
                                            val result = onCellCommit(
                                                actualRowIdx,
                                                colName,
                                                newValue,
                                                rowSnapshot,
                                            )
                                            if (result.isFailure) {
                                                val msg = result.exceptionOrNull()?.message ?: "Failed to persist update"
                                                gridError = msg
                                                localRows = localRows.toMutableList().apply {
                                                    this[actualRowIdx] = this[actualRowIdx].toMutableList().apply {
                                                        this[cIdx] = original
                                                    }
                                                }
                                            } else {
                                                baselineRows = baselineRows.toMutableList().apply {
                                                    this[actualRowIdx] = this[actualRowIdx].toMutableList().apply {
                                                        this[cIdx] = newValue
                                                    }
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditableCell(
    value: String,
    isDirty: Boolean,
    onValueChange: (String) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .width(150.dp)
            .border(
                width = if (isDirty) 2.dp else 1.dp,
                color = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
            .background(
                if (isDirty) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { isEditing = true },
                )
            }
            .padding(8.dp),
    ) {
        if (isEditing) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (!it.isFocused) {
                            isEditing = false
                            onValueChange(text)
                        }
                    }
                    .onKeyEvent {
                        if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                            isEditing = false
                            onValueChange(text)
                            true
                        } else if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                            isEditing = false
                            text = value
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        } else {
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
