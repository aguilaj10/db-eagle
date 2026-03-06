package com.dbeagle.ddl

/**
 * SQLite implementation of DDLDialect.
 *
 * SQLite uses a type affinity system (TEXT, NUMERIC, INTEGER, REAL, BLOB)
 * and has limited ALTER TABLE support compared to PostgreSQL.
 *
 * Key SQLite characteristics:
 * - No native sequence support (uses AUTOINCREMENT with INTEGER PRIMARY KEY)
 * - Limited ALTER TABLE: cannot modify or drop columns without table recreation
 * - Supports IF EXISTS/IF NOT EXISTS for most DDL operations
 * - Uses double quotes for identifier quoting (SQL-standard)
 */
object SQLiteDDLDialect : DDLDialect {

    /**
     * Quotes identifiers using double quotes (SQL-standard).
     *
     * Embedded double quotes are escaped by doubling them.
     * Example: table"name -> "table""name"
     */
    override fun quoteIdentifier(name: String): String {
        val escaped = name.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * SQLite does not support CREATE SEQUENCE.
     * Uses AUTOINCREMENT on INTEGER PRIMARY KEY instead.
     */
    override fun supportsSequences(): Boolean = false

    /**
     * SQLite has limited ALTER COLUMN support.
     * Modifying column definitions requires recreating the table.
     */
    override fun supportsAlterColumn(): Boolean = false

    /**
     * SQLite has limited DROP COLUMN support.
     * Dropping columns requires recreating the table (before SQLite 3.35.0).
     */
    override fun supportsDropColumn(): Boolean = false

    /**
     * SQLite supports IF EXISTS and IF NOT EXISTS clauses.
     */
    override fun supportsIfExists(): Boolean = true

    /**
     * Maps generic ColumnType to SQLite type affinity names.
     *
     * SQLite type affinity rules:
     * - TEXT: Variable-length text
     * - INTEGER: All integer types (no distinction between INT, BIGINT, etc.)
     * - REAL: Floating point and decimal numbers
     * - BLOB: Binary data
     * - Dates/timestamps stored as TEXT in ISO8601 format
     * - Boolean stored as INTEGER (0/1)
     */
    override fun getTypeName(genericType: ColumnType): String = when (genericType) {
        ColumnType.TEXT -> "TEXT"
        ColumnType.INTEGER -> "INTEGER"
        ColumnType.BIGINT -> "INTEGER" // SQLite stores all integers as INTEGER
        ColumnType.DECIMAL -> "REAL"
        ColumnType.BOOLEAN -> "INTEGER" // SQLite has no native boolean (uses 0/1)
        ColumnType.DATE -> "TEXT" // Stored as ISO8601 string: YYYY-MM-DD
        ColumnType.TIMESTAMP -> "TEXT" // Stored as ISO8601 string: YYYY-MM-DD HH:MM:SS.SSS
        ColumnType.BLOB -> "BLOB"
        ColumnType.SMALLINT -> "INTEGER"
        ColumnType.REAL -> "REAL"
        ColumnType.DOUBLE_PRECISION -> "REAL"
        ColumnType.UUID -> "TEXT"
        ColumnType.JSON -> "TEXT"
        ColumnType.JSONB -> "BLOB"
        ColumnType.SERIAL -> "INTEGER"
        ColumnType.SMALLSERIAL -> "INTEGER"
        ColumnType.BIGSERIAL -> "INTEGER"
    }
}
