package com.dbeagle.ui.dialogs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.dbeagle.ui.SettingsScreen
import java.awt.Dimension

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = DpSize(500.dp, 600.dp)),
        title = "Settings",
        resizable = true,
    ) {
        window.minimumSize = Dimension(600, 400)
        SettingsScreen(
            onClose = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
