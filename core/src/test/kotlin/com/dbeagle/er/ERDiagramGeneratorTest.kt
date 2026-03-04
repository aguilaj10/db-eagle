package com.dbeagle.er

import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.TableMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class ERDiagramGeneratorTest {
    @Test
    fun `generate builds nodes and edges for single schema`() {
        val schema =
            SchemaMetadata(
                tables =
                listOf(
                    table(schema = "public", name = "accounts"),
                    table(schema = "public", name = "users"),
                    table(schema = "public", name = "orders"),
                    table(schema = "public", name = "order_items"),
                    table(schema = "public", name = "products"),
                    table(schema = "other", name = "audit_log"),
                ),
                foreignKeys =
                listOf(
                    ForeignKeyRelationship(
                        fromTable = "users",
                        fromColumn = "account_id",
                        toTable = "accounts",
                        toColumn = "id",
                    ),
                    ForeignKeyRelationship(
                        fromTable = "orders",
                        fromColumn = "user_id",
                        toTable = "users",
                        toColumn = "id",
                    ),
                    ForeignKeyRelationship(
                        fromTable = "order_items",
                        fromColumn = "order_id",
                        toTable = "orders",
                        toColumn = "id",
                    ),
                    ForeignKeyRelationship(
                        fromTable = "audit_log",
                        fromColumn = "user_id",
                        toTable = "users",
                        toColumn = "id",
                    ),
                ),
            )

        val diagram = ERDiagramGenerator.generate(schemaMetadata = schema, schema = "public")

        assertEquals(
            listOf("accounts", "order_items", "orders", "products", "users"),
            diagram.nodes.map { it.table },
        )

        assertEquals(
            listOf(
                "order_items.order_id -> orders.id",
                "orders.user_id -> users.id",
                "users.account_id -> accounts.id",
            ),
            diagram.edges.map { "${it.from.table}.${it.fromColumn} -> ${it.to.table}.${it.toColumn}" },
        )

        assertEquals(true, diagram.nodes.all { it.schema == "public" })
        assertEquals(true, diagram.edges.all { it.from.schema == "public" && it.to.schema == "public" })
    }
}

private fun table(
    schema: String,
    name: String,
): TableMetadata = TableMetadata(
    name = name,
    schema = schema,
    columns =
    listOf(
        ColumnMetadata(name = "id", type = "INTEGER", nullable = false),
    ),
)
