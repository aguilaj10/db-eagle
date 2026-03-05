package com.dbeagle.ddl

/**
 * Represents generic column types that map to database-specific types.
 *
 * These types provide a database-agnostic abstraction layer for DDL generation.
 * Each database dialect will map these generic types to their native SQL types.
 */
enum class ColumnType {
    /** Variable-length text data */
    TEXT,

    /** 32-bit integer */
    INTEGER,

    /** 64-bit integer */
    BIGINT,

    /** Fixed-point decimal number */
    DECIMAL,

    /** Boolean (true/false) value */
    BOOLEAN,

    /** Date without time */
    DATE,

    /** Date and time with timezone */
    TIMESTAMP,

    /** Binary large object */
    BLOB,
}

/**
 * Abstraction for database-specific DDL syntax differences.
 *
 * Different databases have varying support for DDL features and use different
 * syntax for common operations. This interface allows DDL builders to generate
 * correct SQL for each target database.
 *
 * Implementations should provide:
 * - Database-specific identifier quoting rules
 * - Feature capability flags (sequences, ALTER operations, etc.)
 * - Type name mappings from generic ColumnType to native SQL types
 */
interface DDLDialect {

    /**
     * Quotes an identifier (table/column name) according to database rules.
     *
     * Different databases use different quoting conventions:
     * - PostgreSQL: "identifier"
     * - SQLite: "identifier" or [identifier]
     * - MySQL: `identifier`
     *
     * @param name The unquoted identifier name
     * @return The properly quoted identifier for this database
     */
    fun quoteIdentifier(name: String): String

    /**
     * Returns true if the database supports sequences.
     *
     * Sequences provide independent auto-incrementing values.
     * - PostgreSQL: Supported via CREATE SEQUENCE
     * - SQLite: Not supported (uses AUTOINCREMENT)
     *
     * @return true if sequences are supported, false otherwise
     */
    fun supportsSequences(): Boolean

    /**
     * Returns true if the database supports ALTER COLUMN operations.
     *
     * ALTER COLUMN allows modifying column definitions after creation.
     * - PostgreSQL: Fully supported
     * - SQLite: Limited support (requires table recreation)
     *
     * @return true if ALTER COLUMN is supported, false otherwise
     */
    fun supportsAlterColumn(): Boolean

    /**
     * Returns true if the database supports DROP COLUMN operations.
     *
     * DROP COLUMN allows removing columns from existing tables.
     * - PostgreSQL: Fully supported
     * - SQLite: Limited support (requires table recreation in older versions)
     *
     * @return true if DROP COLUMN is supported, false otherwise
     */
    fun supportsDropColumn(): Boolean

    /**
     * Returns true if the database supports IF EXISTS/IF NOT EXISTS clauses.
     *
     * These clauses allow idempotent DDL operations that don't fail if
     * objects already exist or don't exist.
     * - PostgreSQL: Fully supported
     * - SQLite: Supported for most operations
     *
     * @return true if IF EXISTS is supported, false otherwise
     */
    fun supportsIfExists(): Boolean

    /**
     * Maps a generic ColumnType to the database-specific type name.
     *
     * Each database has different native type names:
     * - TEXT: VARCHAR (PostgreSQL), TEXT (SQLite)
     * - BIGINT: BIGINT (PostgreSQL), INTEGER (SQLite)
     * - BOOLEAN: BOOLEAN (PostgreSQL), INTEGER (SQLite)
     *
     * @param genericType The generic column type to map
     * @return The database-specific SQL type name
     */
    fun getTypeName(genericType: ColumnType): String
}
