package com.dbeagle.ddl

import kotlinx.serialization.Serializable

@Serializable
data class IndexDefinition(
    val name: String,
    val tableName: String,
    val columns: List<String>,
    val unique: Boolean = false,
)

object IndexDDLBuilder {

    fun buildCreateIndex(dialect: DDLDialect, index: IndexDefinition): String = buildString {
        append("CREATE ")
        if (index.unique) {
            append("UNIQUE ")
        }
        append("INDEX ")
        append(dialect.quoteIdentifier(index.name))
        append(" ON ")
        append(dialect.quoteIdentifier(index.tableName))
        append(" (")
        append(index.columns.joinToString(", ") { dialect.quoteIdentifier(it) })
        append(")")
    }

    fun buildDropIndex(
        dialect: DDLDialect,
        indexName: String,
        tableName: String? = null,
        ifExists: Boolean = true,
    ): String = buildString {
        append("DROP INDEX ")
        if (ifExists && dialect.supportsIfExists()) {
            append("IF EXISTS ")
        }
        append(dialect.quoteIdentifier(indexName))
    }
}
