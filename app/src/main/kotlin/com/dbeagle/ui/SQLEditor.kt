package com.dbeagle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wakaztahir.codeeditor.model.CodeLang
import com.wakaztahir.codeeditor.prettify.PrettifyParser
import com.wakaztahir.codeeditor.theme.CodeThemeType
import com.wakaztahir.codeeditor.utils.parseCodeAsAnnotatedString

@Composable
fun SQLEditor(
    sql: String,
    onSqlChange: (String) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parser = remember { PrettifyParser() }
    val theme = remember { CodeThemeType.Monokai.theme }

    fun parseSQL(code: String) = parseCodeAsAnnotatedString(
        parser = parser,
        theme = theme,
        lang = CodeLang.SQL,
        code = code,
    )

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                annotatedString = parseSQL(sql),
            ),
        )
    }
    
    // Sync external changes without losing cursor position
    var lastExternalSql by remember { mutableStateOf(sql) }
    if (sql != lastExternalSql && sql != textFieldValue.text) {
        textFieldValue = TextFieldValue(
            annotatedString = parseSQL(sql),
            selection = textFieldValue.selection,
        )
        lastExternalSql = sql
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    event.key == Key.Enter &&
                    (event.isCtrlPressed || event.isMetaPressed)
                ) {
                    if (!isRunning) {
                        onRun()
                    }
                    true
                } else {
                    false
                }
            },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            val lineCount = textFieldValue.text.lines().size
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 16.dp),
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString(),
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.small,
                    ),
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue.copy(
                            annotatedString = parseSQL(newValue.text),
                        )
                        onSqlChange(newValue.text)
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .onFocusChanged { isFocused = it.hasFocus },
                )
            }
        }
    }
}
