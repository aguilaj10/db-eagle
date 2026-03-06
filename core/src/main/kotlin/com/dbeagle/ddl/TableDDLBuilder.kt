package com.dbeagle.ddl

import kotlinx.serialization.Serializable

/**
 * Represents a column definition for DDL generation.
 */
@Serializable
data class ColumnDefinition(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean = true,
    val defaultValue: String? = null,
    val autoIncrement: Boolean = false,
)

/**
 * Represents a foreign key constraint definition.
 */
@Serializable
data class ForeignKeyDefinition(
    val name: String? = null,
    val columns: List<String>,
    val refTable: String,
    val refColumns: List<String>,
    val onDelete: String? = null,
    val onUpdate: String? = null,
)

/**
 * Sealed class representing different types of table constraints.
 */
@Serializable
sealed class ConstraintDefinition {
    @Serializable
    data class PrimaryKey(val columns: List<String>) : ConstraintDefinition()

    @Serializable
    data class ForeignKey(val def: ForeignKeyDefinition) : ConstraintDefinition()

    @Serializable
    data class Unique(val name: String?, val columns: List<String>) : ConstraintDefinition()
}

/**
 * Represents a complete table definition for DDL generation.
 */
@Serializable
data class TableDefinition(
    val name: String,
    val columns: List<ColumnDefinition>,
    val primaryKey: List<String>? = null,
    val foreignKeys: List<ForeignKeyDefinition> = emptyList(),
    val uniqueConstraints: List<List<String>> = emptyList(),
)

/**
 * Builds DDL statements for table operations.
 *
 * Generates CREATE TABLE, ALTER TABLE, and DROP TABLE statements using
 * database-specific DDLDialect implementations for proper syntax.
 */
object TableDDLBuilder {

    /**
     * Builds a CREATE TABLE statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param table The table definition to create
     * @return A complete CREATE TABLE DDL statement
     */
    fun buildCreateTable(dialect: DDLDialect, table: TableDefinition): String = buildString {
        append("CREATE TABLE ${dialect.quoteIdentifier(table.name)} (")

        // Add column definitions
        val columnDefs = table.columns.map { col ->
            buildString {
                append(dialect.quoteIdentifier(col.name))
                append(" ")
                append(dialect.getTypeName(col.type, col.autoIncrement))
                if (!col.nullable || col.autoIncrement) {
                    append(" NOT NULL")
                }
                col.defaultValue?.let { default ->
                    append(" DEFAULT $default")
                }
            }
        }
        append(columnDefs.joinToString(", "))

        // Add primary key constraint
        table.primaryKey?.takeIf { it.isNotEmpty() }?.let { pk ->
            append(", PRIMARY KEY (")
            append(pk.joinToString(", ") { dialect.quoteIdentifier(it) })
            append(")")
        }

        // Add unique constraints
        table.uniqueConstraints.forEach { uniqueCols ->
            if (uniqueCols.isNotEmpty()) {
                append(", UNIQUE (")
                append(uniqueCols.joinToString(", ") { dialect.quoteIdentifier(it) })
                append(")")
            }
        }

        // Add foreign key constraints
        table.foreignKeys.forEach { fk ->
            append(", ")
            fk.name?.let { name ->
                append("CONSTRAINT ${dialect.quoteIdentifier(name)} ")
            }
            append("FOREIGN KEY (")
            append(fk.columns.joinToString(", ") { dialect.quoteIdentifier(it) })
            append(") REFERENCES ${dialect.quoteIdentifier(fk.refTable)} (")
            append(fk.refColumns.joinToString(", ") { dialect.quoteIdentifier(it) })
            append(")")
            fk.onDelete?.let { append(" ON DELETE $it") }
            fk.onUpdate?.let { append(" ON UPDATE $it") }
        }

        append(")")
    }

    /**
     * Builds an ALTER TABLE ADD COLUMN statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param table The table name to alter
     * @param column The column definition to add
     * @return An ALTER TABLE ADD COLUMN DDL statement
     */
    fun buildAlterTableAddColumn(
        dialect: DDLDialect,
        table: String,
        column: ColumnDefinition,
    ): String = buildString {
        append("ALTER TABLE ${dialect.quoteIdentifier(table)} ADD COLUMN ")
        append(dialect.quoteIdentifier(column.name))
        append(" ")
        append(dialect.getTypeName(column.type, column.autoIncrement))
        if (!column.nullable || column.autoIncrement) {
            append(" NOT NULL")
        }
        column.defaultValue?.let { default ->
            append(" DEFAULT $default")
        }
    }

    /**
     * Builds an ALTER TABLE DROP COLUMN statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param table The table name to alter
     * @param column The column name to drop
     * @return An ALTER TABLE DROP COLUMN DDL statement
     * @throws UnsupportedOperationException if the dialect doesn't support DROP COLUMN
     */
    fun buildAlterTableDropColumn(
        dialect: DDLDialect,
        table: String,
        column: String,
    ): String {
        if (!dialect.supportsDropColumn()) {
            throw UnsupportedOperationException(
                "DROP COLUMN is not supported by this database dialect. " +
                    "Table recreation may be required.",
            )
        }
        return "ALTER TABLE ${dialect.quoteIdentifier(table)} DROP COLUMN ${dialect.quoteIdentifier(column)}"
    }

    /**
     * Builds an ALTER TABLE ADD CONSTRAINT statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param table The table name to alter
     * @param constraint The constraint definition to add
     * @return An ALTER TABLE ADD CONSTRAINT DDL statement
     */
    fun buildAlterTableAddConstraint(
        dialect: DDLDialect,
        table: String,
        constraint: ConstraintDefinition,
    ): String = buildString {
        append("ALTER TABLE ${dialect.quoteIdentifier(table)} ADD ")

        when (constraint) {
            is ConstraintDefinition.PrimaryKey -> {
                append("PRIMARY KEY (")
                append(constraint.columns.joinToString(", ") { dialect.quoteIdentifier(it) })
                append(")")
            }
            is ConstraintDefinition.ForeignKey -> {
                val fk = constraint.def
                fk.name?.let { name ->
                    append("CONSTRAINT ${dialect.quoteIdentifier(name)} ")
                }
                append("FOREIGN KEY (")
                append(fk.columns.joinToString(", ") { dialect.quoteIdentifier(it) })
                append(") REFERENCES ${dialect.quoteIdentifier(fk.refTable)} (")
                append(fk.refColumns.joinToString(", ") { dialect.quoteIdentifier(it) })
                append(")")
                fk.onDelete?.let { append(" ON DELETE $it") }
                fk.onUpdate?.let { append(" ON UPDATE $it") }
            }
            is ConstraintDefinition.Unique -> {
                constraint.name?.let { name ->
                    append("CONSTRAINT ${dialect.quoteIdentifier(name)} ")
                }
                append("UNIQUE (")
                append(constraint.columns.joinToString(", ") { dialect.quoteIdentifier(it) })
                append(")")
            }
        }
    }

    /**
     * Builds an ALTER TABLE DROP CONSTRAINT statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param table The table name to alter
     * @param constraintName The constraint name to drop
     * @return An ALTER TABLE DROP CONSTRAINT DDL statement
     */
    fun buildAlterTableDropConstraint(
        dialect: DDLDialect,
        table: String,
        constraintName: String,
    ): String = "ALTER TABLE ${dialect.quoteIdentifier(table)} DROP CONSTRAINT ${dialect.quoteIdentifier(constraintName)}"

    /**
     * Builds a DROP TABLE statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param table The table name to drop
     * @param cascade If true, cascades the drop to dependent objects
     * @return A DROP TABLE DDL statement
     */
    fun buildDropTable(
        dialect: DDLDialect,
        table: String,
        cascade: Boolean = false,
    ): String = buildString {
        append("DROP TABLE ")
        if (dialect.supportsIfExists()) {
            append("IF EXISTS ")
        }
        append(dialect.quoteIdentifier(table))
        if (cascade) {
            append(" CASCADE")
        }
    }
}
