package com.dbeagle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import dev.snipme.kodeview.view.material3.CodeEditText

@Composable
fun SQLEditor(
    sql: String,
    onSqlChange: (String) -> Unit,
    onRun: () -> Unit,
    isRunning: Boolean,
    onClear: () -> Unit,
    onSaveToFavorites: () -> Unit,
    modifier: Modifier = Modifier
) {
    var highlights by remember {
        mutableStateOf(
            Highlights.Builder()
                .code(sql)
                .language(SyntaxLanguage.DEFAULT)
                .theme(SyntaxThemes.atom(darkMode = true))
                .build()
        )
    }

    LaunchedEffect(sql) {
        if (highlights.getCode() != sql) {
            highlights = highlights.getBuilder().code(sql).build()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onRun, enabled = !isRunning) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run Query")
                }
                Spacer(Modifier.width(4.dp))
                Text(if (isRunning) "Running" else "Run")
            }
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
            OutlinedButton(onClick = onSaveToFavorites) {
                Icon(Icons.Default.Favorite, contentDescription = "Save")
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        }

        HorizontalDivider()

        // Editor Area
        Row(modifier = Modifier.fillMaxSize()) {
            // Line numbers
            val lineCount = sql.lines().size
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 16.dp)
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString(),
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Code Editor
            CodeEditText(
                highlights = highlights,
                onValueChange = { newSql ->
                    highlights = highlights.getBuilder().code(newSql).build()
                    onSqlChange(newSql)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }
}
