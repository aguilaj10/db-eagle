package com.dbeagle.ddl

/**
 * Builds DDL statements for view operations.
 *
 * Generates CREATE VIEW, CREATE OR REPLACE VIEW, and DROP VIEW statements
 * using database-specific DDLDialect implementations for proper syntax.
 *
 * Supports:
 * - PostgreSQL: Full support for CREATE OR REPLACE, IF EXISTS, CASCADE
 * - SQLite: CREATE VIEW, DROP VIEW IF EXISTS
 * - Oracle: CREATE OR REPLACE VIEW, DROP VIEW (no IF EXISTS)
 */
object ViewDDLBuilder {

    /**
     * Builds a CREATE VIEW or CREATE OR REPLACE VIEW statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param name The view name
     * @param selectQuery The SELECT query defining the view
     * @param schema Optional schema name for the view
     * @param orReplace If true, uses CREATE OR REPLACE VIEW (when supported)
     * @return A complete CREATE VIEW DDL statement
     */
    fun buildCreateView(
        dialect: DDLDialect,
        name: String,
        selectQuery: String,
        schema: String? = null,
        orReplace: Boolean = false,
    ): String = buildString {
        append("CREATE ")
        if (orReplace) {
            append("OR REPLACE ")
        }
        append("VIEW ")

        // Build qualified view name
        val qualifiedName = if (schema != null) {
            "${dialect.quoteIdentifier(schema)}.${dialect.quoteIdentifier(name)}"
        } else {
            dialect.quoteIdentifier(name)
        }
        append(qualifiedName)

        append(" AS ")
        append(selectQuery)
    }

    /**
     * Builds a DROP VIEW statement.
     *
     * @param dialect The database dialect for syntax rules
     * @param name The view name to drop
     * @param schema Optional schema name for the view
     * @param ifExists If true, uses IF EXISTS clause (when supported by dialect)
     * @param cascade If true, cascades the drop to dependent objects (PostgreSQL only)
     * @return A DROP VIEW DDL statement
     */
    fun buildDropView(
        dialect: DDLDialect,
        name: String,
        schema: String? = null,
        ifExists: Boolean = true,
        cascade: Boolean = false,
    ): String = buildString {
        append("DROP VIEW ")

        if (ifExists && dialect.supportsIfExists()) {
            append("IF EXISTS ")
        }

        // Build qualified view name
        val qualifiedName = if (schema != null) {
            "${dialect.quoteIdentifier(schema)}.${dialect.quoteIdentifier(name)}"
        } else {
            dialect.quoteIdentifier(name)
        }
        append(qualifiedName)

        if (cascade) {
            append(" CASCADE")
        }
    }
}
