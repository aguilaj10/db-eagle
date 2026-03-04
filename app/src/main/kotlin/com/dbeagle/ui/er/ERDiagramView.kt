package com.dbeagle.ui.er

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.dbeagle.model.ERDiagram
import com.dbeagle.model.ERTableNode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders an ER diagram using a deterministic grid layout.
 * Supports basic pan and zoom via touch/mouse drag and scroll.
 *
 * Coordinate system:
 * - Origin (0,0) is at the top-left of the view.
 * - Nodes are arranged in a grid with a fixed number of columns.
 * - Edges are drawn between the calculated center points of the nodes.
 */
@Composable
fun ERDiagramView(diagram: ERDiagram, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.1f, 5f)
                    offset += pan
                }
            },
    ) {
        val tableWidth = 200f
        val tableHeight = 150f
        val spacingX = 100f
        val spacingY = 100f
        val columns = 3

        val nodePositions = mutableMapOf<ERTableNode, Offset>()
        diagram.nodes.forEachIndexed { index, node ->
            val row = index / columns
            val col = index % columns
            val x = col * (tableWidth + spacingX)
            val y = row * (tableHeight + spacingY)
            nodePositions[node] = Offset(x, y)
        }

        withTransform({
            translate(left = offset.x, top = offset.y)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            diagram.edges.forEach { edge ->
                val startNodePos = nodePositions[edge.from]
                val endNodePos = nodePositions[edge.to]

                if (startNodePos != null && endNodePos != null) {
                    val startCenter = Offset(
                        startNodePos.x + tableWidth / 2,
                        startNodePos.y + tableHeight / 2,
                    )
                    val endCenter = Offset(
                        endNodePos.x + tableWidth / 2,
                        endNodePos.y + tableHeight / 2,
                    )

                    drawLine(
                        color = Color.Gray,
                        start = startCenter,
                        end = endCenter,
                        strokeWidth = 2f,
                    )

                    drawArrowhead(startCenter, endCenter)
                }
            }

            diagram.nodes.forEach { node ->
                val pos = nodePositions[node] ?: return@forEach

                drawRect(
                    color = Color.LightGray,
                    topLeft = pos,
                    size = Size(tableWidth, tableHeight),
                )
                drawRect(
                    color = Color.Black,
                    topLeft = pos,
                    size = Size(tableWidth, tableHeight),
                    style = Stroke(width = 2f),
                )

                drawLine(
                    color = Color.Black,
                    start = Offset(pos.x, pos.y + 40f),
                    end = Offset(pos.x + tableWidth, pos.y + 40f),
                    strokeWidth = 2f,
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = "${node.schema}.${node.table}",
                    topLeft = Offset(pos.x + 10f, pos.y + 10f),
                    style = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = "id (PK)\ncolumn1\ncolumn2",
                    topLeft = Offset(pos.x + 10f, pos.y + 50f),
                    style = TextStyle(color = Color.DarkGray, fontSize = 12.sp),
                )
            }
        }
    }
}

private fun DrawScope.drawArrowhead(start: Offset, end: Offset) {
    val arrowSize = 15f
    val angle = atan2(end.y - start.y, end.x - start.x)

    val arrowEnd = Offset(
        end.x - cos(angle) * (200f / 2),
        end.y - sin(angle) * (150f / 2),
    )

    val path = Path().apply {
        moveTo(arrowEnd.x, arrowEnd.y)
        lineTo(
            arrowEnd.x - arrowSize * cos(angle - Math.PI / 6).toFloat(),
            arrowEnd.y - arrowSize * sin(angle - Math.PI / 6).toFloat(),
        )
        lineTo(
            arrowEnd.x - arrowSize * cos(angle + Math.PI / 6).toFloat(),
            arrowEnd.y - arrowSize * sin(angle + Math.PI / 6).toFloat(),
        )
        close()
    }

    drawPath(path = path, color = Color.Gray)
}
