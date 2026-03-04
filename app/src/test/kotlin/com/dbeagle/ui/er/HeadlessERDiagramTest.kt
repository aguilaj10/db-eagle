package com.dbeagle.ui.er

import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import java.io.File
import kotlin.test.Test
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import com.dbeagle.model.ERDiagram
import com.dbeagle.model.ERForeignKeyEdge
import com.dbeagle.model.ERTableNode
import org.jetbrains.skia.EncodedImageFormat

class HeadlessERDiagramTest {

    @Test
    fun generateERDiagramPng() {
        val node1 = ERTableNode("public", "users")
        val node2 = ERTableNode("public", "orders")
        val edge = ERForeignKeyEdge(node2, "user_id", node1, "id")
        
        val diagram = ERDiagram(
            nodes = listOf(node1, node2),
            edges = listOf(edge)
        )

        val width = 800
        val height = 600

        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f)
        )

        scene.setContent {
            Surface(color = Color.White) {
                ERDiagramView(diagram)
            }
        }

        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
        val bytes = data?.bytes ?: throw IllegalStateException("Failed to encode image")
        
        val outputFile = File("../.sisyphus/evidence/task-29-er-render.png")
        outputFile.parentFile.mkdirs()
        outputFile.writeBytes(bytes)
        
        scene.close()
    }
}
