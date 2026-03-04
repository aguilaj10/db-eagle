package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
data class ERDiagram(
    val nodes: List<ERTableNode>,
    val edges: List<ERForeignKeyEdge>,
)

@Serializable
data class ERTableNode(
    val schema: String,
    val table: String,
)

@Serializable
data class ERForeignKeyEdge(
    val from: ERTableNode,
    val fromColumn: String,
    val to: ERTableNode,
    val toColumn: String,
)
