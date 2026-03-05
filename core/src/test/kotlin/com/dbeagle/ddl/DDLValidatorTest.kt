package com.dbeagle.ddl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DDLValidatorTest {

    @Test
    fun `validateIdentifier accepts valid simple name`() {
        val result = DDLValidator.validateIdentifier("users")

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateIdentifier accepts name with underscores`() {
        val result = DDLValidator.validateIdentifier("user_accounts")

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateIdentifier accepts name with numbers`() {
        val result = DDLValidator.validateIdentifier("table123")

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateIdentifier accepts name with dollar sign`() {
        val result = DDLValidator.validateIdentifier("user\$data")

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateIdentifier accepts name starting with underscore`() {
        val result = DDLValidator.validateIdentifier("_private_table")

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateIdentifier rejects empty string`() {
        val result = DDLValidator.validateIdentifier("")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("empty or blank") })
    }

    @Test
    fun `validateIdentifier rejects blank string`() {
        val result = DDLValidator.validateIdentifier("   ")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("empty or blank") })
    }

    @Test
    fun `validateIdentifier rejects name exceeding max length`() {
        val longName = "a".repeat(129)

        val result = DDLValidator.validateIdentifier(longName)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("exceeds maximum length") })
    }

    @Test
    fun `validateIdentifier rejects name with semicolon`() {
        val result = DDLValidator.validateIdentifier("users;DROP TABLE")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains(";") })
    }

    @Test
    fun `validateIdentifier rejects name with SQL comment pattern`() {
        val result = DDLValidator.validateIdentifier("users--comment")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("--") })
    }

    @Test
    fun `validateIdentifier rejects name with block comment start`() {
        val result = DDLValidator.validateIdentifier("users/*comment")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("/*") })
    }

    @Test
    fun `validateIdentifier rejects name with DROP keyword`() {
        val result = DDLValidator.validateIdentifier("DROP_TABLE")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("DROP") })
    }

    @Test
    fun `validateIdentifier rejects name with DELETE keyword`() {
        val result = DDLValidator.validateIdentifier("DELETE_USER")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("DELETE") })
    }

    @Test
    fun `validateIdentifier rejects name with INSERT keyword`() {
        val result = DDLValidator.validateIdentifier("INSERT_DATA")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("INSERT") })
    }

    @Test
    fun `validateIdentifier rejects name with UPDATE keyword`() {
        val result = DDLValidator.validateIdentifier("UPDATE_TABLE")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("UPDATE") })
    }

    @Test
    fun `validateIdentifier rejects name with SELECT keyword`() {
        val result = DDLValidator.validateIdentifier("SELECT_ALL")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("SELECT") })
    }

    @Test
    fun `validateIdentifier rejects name starting with number`() {
        val result = DDLValidator.validateIdentifier("123table")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("invalid characters") })
    }

    @Test
    fun `validateIdentifier rejects name with space`() {
        val result = DDLValidator.validateIdentifier("user table")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("invalid characters") })
    }

    @Test
    fun `validateIdentifier rejects name with hyphen`() {
        val result = DDLValidator.validateIdentifier("user-table")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("invalid characters") })
    }

    @Test
    fun `validateIdentifier rejects name with dot`() {
        val result = DDLValidator.validateIdentifier("schema.table")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("invalid characters") })
    }

    @Test
    fun `validateIdentifier case insensitive SQL injection detection`() {
        val result = DDLValidator.validateIdentifier("select_data")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("SELECT") })
    }

    @Test
    fun `validateTableDefinition accepts valid table`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("email", ColumnType.TEXT, nullable = false)
            )
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateTableDefinition rejects table with no columns`() {
        val table = TableDefinition(
            name = "users",
            columns = emptyList()
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("at least one column") })
    }

    @Test
    fun `validateTableDefinition rejects table with invalid name`() {
        val table = TableDefinition(
            name = "users;DROP",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false)
            )
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid table name") })
    }

    @Test
    fun `validateTableDefinition rejects duplicate column names`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("id", ColumnType.TEXT, nullable = false)
            )
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Duplicate column names") && it.contains("id") })
    }

    @Test
    fun `validateTableDefinition rejects invalid column name`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("email;DROP", ColumnType.TEXT, nullable = false)
            )
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid column 'email;DROP'") })
    }

    @Test
    fun `validateTableDefinition collects multiple errors`() {
        val table = TableDefinition(
            name = "users;DROP",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("id", ColumnType.TEXT, nullable = false),
                ColumnDefinition("email--", ColumnType.TEXT, nullable = false)
            )
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.size > 1)
        assertTrue(errors.any { it.contains("Invalid table name") })
        assertTrue(errors.any { it.contains("Duplicate column names") })
        assertTrue(errors.any { it.contains("Invalid column") })
    }

    @Test
    fun `validateColumnDefinition accepts valid column`() {
        val column = ColumnDefinition("user_id", ColumnType.INTEGER, nullable = false)

        val result = DDLValidator.validateColumnDefinition(column)

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateColumnDefinition rejects invalid column name`() {
        val column = ColumnDefinition("user id", ColumnType.TEXT, nullable = false)

        val result = DDLValidator.validateColumnDefinition(column)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid column name") })
    }

    @Test
    fun `validateIdentifier accepts exactly 128 characters`() {
        val name = "a".repeat(128)

        val result = DDLValidator.validateIdentifier(name)

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateTableDefinition accepts complex valid table`() {
        val table = TableDefinition(
            name = "orders",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("user_id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("status", ColumnType.TEXT, nullable = false, defaultValue = "'pending'"),
                ColumnDefinition("created_at", ColumnType.TIMESTAMP, nullable = false)
            ),
            primaryKey = listOf("id"),
            foreignKeys = listOf(
                ForeignKeyDefinition(
                    name = "fk_user",
                    columns = listOf("user_id"),
                    refTable = "users",
                    refColumns = listOf("id")
                )
            ),
            uniqueConstraints = listOf(listOf("user_id", "created_at"))
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateIdentifier SQL injection with mixed case attempt`() {
        val result = DDLValidator.validateIdentifier("UsErS_DeLeTe")

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("dangerous pattern") && it.contains("DELETE") })
    }

    @Test
    fun `validateTableDefinition indents nested errors`() {
        val table = TableDefinition(
            name = "valid_table",
            columns = listOf(
                ColumnDefinition("123invalid", ColumnType.INTEGER, nullable = false)
            )
        )

        val result = DDLValidator.validateTableDefinition(table)

        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid column '123invalid'") })
        assertTrue(errors.any { it.contains("  - ") })
    }
}
