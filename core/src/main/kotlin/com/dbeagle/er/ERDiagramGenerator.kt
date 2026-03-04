package com.dbeagle.er

import com.dbeagle.model.ERDiagram
import com.dbeagle.model.ERForeignKeyEdge
import com.dbeagle.model.ERTableNode
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.SchemaMetadata

object ERDiagramGenerator {
    fun generate(schemaMetadata: SchemaMetadata, schema: String): ERDiagram {
        val tableNamesInSchema = schemaMetadata.tables
            .asSequence()
            .filter { it.schema == schema }
            .map { it.name }
            .toSet()

        val nodes = tableNamesInSchema
            .asSequence()
            .sorted()
            .map { ERTableNode(schema = schema, table = it) }
            .toList()

        val edges = schemaMetadata.foreignKeys
            .asSequence()
            .filter { it.fromTable in tableNamesInSchema && it.toTable in tableNamesInSchema }
            .map { it.toEdge(schema = schema) }
            .distinct()
            .sortedWith(
                compareBy<ERForeignKeyEdge> { it.from.table }
                    .thenBy { it.fromColumn }
                    .thenBy { it.to.table }
                    .thenBy { it.toColumn }
            )
            .toList()

        return ERDiagram(nodes = nodes, edges = edges)
    }
}

private fun ForeignKeyRelationship.toEdge(schema: String): ERForeignKeyEdge {
    return ERForeignKeyEdge(
        from = ERTableNode(schema = schema, table = fromTable),
        fromColumn = fromColumn,
        to = ERTableNode(schema = schema, table = toTable),
        toColumn = toColumn
    )
}
