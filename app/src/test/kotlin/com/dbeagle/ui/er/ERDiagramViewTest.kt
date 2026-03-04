package com.dbeagle.ui.er

import com.dbeagle.model.ERDiagram
import com.dbeagle.model.ERForeignKeyEdge
import com.dbeagle.model.ERTableNode
import kotlin.test.Test
import kotlin.test.assertEquals

class ERDiagramViewTest {

    @Test
    fun testDiagramDataModelSetup() {
        val node1 = ERTableNode("public", "users")
        val node2 = ERTableNode("public", "orders")
        val edge = ERForeignKeyEdge(node2, "user_id", node1, "id")
        
        val diagram = ERDiagram(
            nodes = listOf(node1, node2),
            edges = listOf(edge)
        )

        assertEquals(2, diagram.nodes.size)
        assertEquals(1, diagram.edges.size)
    }
}
