package com.dbeagle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryEditorToolbar(
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onSaveToFavorites: () -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above,
            ),
            tooltip = { PlainTooltip { Text("Run Query (Ctrl+Enter)") } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = onRun,
                enabled = !isRunning,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run Query",
                )
            }
        }

        if (isRunning) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
                tooltip = { PlainTooltip { Text("Cancel Query") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Cancel Query",
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above,
            ),
            tooltip = { PlainTooltip { Text("Clear Editor") } },
            state = rememberTooltipState(),
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Editor",
                )
            }
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above,
            ),
            tooltip = { PlainTooltip { Text("Save to Favorites") } },
            state = rememberTooltipState(),
        ) {
            IconButton(onClick = onSaveToFavorites) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Save to Favorites",
                )
            }
        }
    }
}
