package com.dbeagle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dbeagle.session.SessionViewModel
import kotlin.math.absoluteValue

private val connectionColors = listOf(
    Color(0xFF2196F3),
    Color(0xFF4CAF50),
    Color(0xFFFF9800),
    Color(0xFF9C27B0),
    Color(0xFFF44336),
    Color(0xFF00BCD4),
    Color(0xFFFFEB3B),
    Color(0xFFE91E63),
)

fun getConnectionColor(profileId: String): Color {
    val index = profileId.hashCode().absoluteValue % connectionColors.size
    return connectionColors[index]
}

@Composable
fun ConnectionDot(
    connectionId: String?,
    sessionStates: Map<String, SessionViewModel.SessionUiState>,
    modifier: Modifier = Modifier,
) {
    val color = if (connectionId != null && sessionStates.containsKey(connectionId)) {
        getConnectionColor(connectionId)
    } else {
        Color.Gray
    }

    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(color = color)
    }
}
