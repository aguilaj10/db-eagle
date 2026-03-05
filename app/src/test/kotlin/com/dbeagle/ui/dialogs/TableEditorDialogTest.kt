package com.dbeagle.ui.dialogs

import com.dbeagle.ddl.ColumnDefinition
import com.dbeagle.ddl.ColumnType
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.ForeignKeyDefinition
import com.dbeagle.ddl.TableDefinition
import com.dbeagle.ddl.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for TableEditorDialog validation logic and data flow.
 * These tests focus on the business logic, not UI rendering.
 */
class TableEditorDialogTest {

    @Test
    fun testTableNameValidation_valid() {
        val validName = "users"
        val result = DDLValidator.validateIdentifier(validName)
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun testTableNameValidation_empty() {
        val emptyName = ""
        val result = DDLValidator.validateIdentifier(emptyName)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun testTableNameValidation_sqlInjection() {
        val maliciousName = "users; DROP TABLE"
        val result = DDLValidator.validateIdentifier(maliciousName)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { it.contains("invalid") || it.contains("reserved keyword") })
    }

    @Test
    fun testTableDefinitionValidation_noColumns() {
        val tableDef = TableDefinition(
            name = "empty_table",
            columns = emptyList(),
            primaryKey = null,
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        val result = DDLValidator.validateTableDefinition(tableDef)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { it.contains("at least one column") })
    }

    @Test
    fun testTableDefinitionValidation_duplicateColumns() {
        val tableDef = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, false, null),
                ColumnDefinition("name", ColumnType.TEXT, true, null),
                ColumnDefinition("id", ColumnType.TEXT, true, null),
            ),
            primaryKey = null,
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        val result = DDLValidator.validateTableDefinition(tableDef)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { it.contains("Duplicate column name") })
    }

    @Test
    fun testTableDefinitionCreation_simpleTable() {
        val columns = listOf(
            ColumnDefinition("id", ColumnType.INTEGER, false, null),
            ColumnDefinition("name", ColumnType.TEXT, false, null),
            ColumnDefinition("email", ColumnType.TEXT, true, null),
        )

        val tableDef = TableDefinition(
            name = "users",
            columns = columns,
            primaryKey = listOf("id"),
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        assertEquals("users", tableDef.name)
        assertEquals(3, tableDef.columns.size)
        assertEquals(listOf("id"), tableDef.primaryKey)
        assertTrue(tableDef.foreignKeys.isEmpty())
    }

    @Test
    fun testTableDefinitionCreation_withForeignKey() {
        val fk = ForeignKeyDefinition(
            name = "fk_order_user",
            columns = listOf("user_id"),
            refTable = "users",
            refColumns = listOf("id"),
            onDelete = "CASCADE",
            onUpdate = null,
        )

        val tableDef = TableDefinition(
            name = "orders",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, false, null),
                ColumnDefinition("user_id", ColumnType.INTEGER, false, null),
            ),
            primaryKey = listOf("id"),
            foreignKeys = listOf(fk),
            uniqueConstraints = emptyList(),
        )

        assertEquals(1, tableDef.foreignKeys.size)
        assertEquals("fk_order_user", tableDef.foreignKeys[0].name)
        assertEquals("users", tableDef.foreignKeys[0].refTable)
        assertEquals(listOf("id"), tableDef.foreignKeys[0].refColumns)
        assertEquals("CASCADE", tableDef.foreignKeys[0].onDelete)
    }

    @Test
    fun testTableDefinitionCreation_withUniqueConstraint() {
        val tableDef = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, false, null),
                ColumnDefinition("email", ColumnType.TEXT, false, null),
                ColumnDefinition("username", ColumnType.TEXT, false, null),
            ),
            primaryKey = listOf("id"),
            foreignKeys = emptyList(),
            uniqueConstraints = listOf(
                listOf("email"),
                listOf("username"),
            ),
        )

        assertEquals(2, tableDef.uniqueConstraints.size)
        assertEquals(listOf("email"), tableDef.uniqueConstraints[0])
        assertEquals(listOf("username"), tableDef.uniqueConstraints[1])
    }

    @Test
    fun testTableDefinitionCreation_compositePrimaryKey() {
        val tableDef = TableDefinition(
            name = "user_roles",
            columns = listOf(
                ColumnDefinition("user_id", ColumnType.INTEGER, false, null),
                ColumnDefinition("role_id", ColumnType.INTEGER, false, null),
            ),
            primaryKey = listOf("user_id", "role_id"),
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        assertEquals(listOf("user_id", "role_id"), tableDef.primaryKey)
    }

    @Test
    fun testTableDefinitionCallback_createMode() {
        val tableName = "products"
        val columns = listOf(
            ColumnDefinition("id", ColumnType.INTEGER, false, null),
            ColumnDefinition("name", ColumnType.TEXT, false, null),
            ColumnDefinition("price", ColumnType.DECIMAL, true, "0.00"),
        )

        val tableDef = TableDefinition(
            name = tableName,
            columns = columns,
            primaryKey = listOf("id"),
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        val result = DDLValidator.validateTableDefinition(tableDef)
        assertIs<ValidationResult.Valid>(result)

        assertEquals("products", tableDef.name)
        assertEquals(3, tableDef.columns.size)
        assertEquals("0.00", tableDef.columns[2].defaultValue)
    }

    @Test
    fun testColumnDefinition_nullableWithDefault() {
        val column = ColumnDefinition(
            name = "status",
            type = ColumnType.TEXT,
            nullable = true,
            defaultValue = "active",
        )

        assertEquals("status", column.name)
        assertEquals(ColumnType.TEXT, column.type)
        assertEquals(true, column.nullable)
        assertEquals("active", column.defaultValue)
    }

    @Test
    fun testColumnDefinition_notNullNoDefault() {
        val column = ColumnDefinition(
            name = "email",
            type = ColumnType.TEXT,
            nullable = false,
            defaultValue = null,
        )

        assertEquals("email", column.name)
        assertEquals(false, column.nullable)
        assertEquals(null, column.defaultValue)
    }

    @Test
    fun testForeignKeyDefinition_withReferentialActions() {
        val fk = ForeignKeyDefinition(
            name = "fk_comment_post",
            columns = listOf("post_id"),
            refTable = "posts",
            refColumns = listOf("id"),
            onDelete = "CASCADE",
            onUpdate = "RESTRICT",
        )

        assertEquals("fk_comment_post", fk.name)
        assertEquals(listOf("post_id"), fk.columns)
        assertEquals("posts", fk.refTable)
        assertEquals(listOf("id"), fk.refColumns)
        assertEquals("CASCADE", fk.onDelete)
        assertEquals("RESTRICT", fk.onUpdate)
    }

    @Test
    fun testForeignKeyDefinition_compositeKey() {
        val fk = ForeignKeyDefinition(
            name = null,
            columns = listOf("user_id", "role_id"),
            refTable = "user_roles",
            refColumns = listOf("user_id", "role_id"),
            onDelete = null,
            onUpdate = null,
        )

        assertEquals(null, fk.name)
        assertEquals(2, fk.columns.size)
        assertEquals(2, fk.refColumns.size)
    }

    @Test
    fun testTableValidation_invalidColumnName() {
        val tableDef = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, false, null),
                ColumnDefinition("user-name", ColumnType.TEXT, true, null),
            ),
            primaryKey = null,
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        val result = DDLValidator.validateTableDefinition(tableDef)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { it.contains("user-name") || it.contains("invalid") })
    }

    @Test
    fun testTableDefinition_primaryKeyNull_whenEmpty() {
        val tableName = "temp_table"
        val columns = listOf(
            ColumnDefinition("data", ColumnType.TEXT, true, null),
        )
        val primaryKeyColumns = emptyList<String>()

        val tableDef = TableDefinition(
            name = tableName,
            columns = columns,
            primaryKey = primaryKeyColumns.takeIf { it.isNotEmpty() },
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        assertEquals(null, tableDef.primaryKey)
    }

    @Test
    fun testTableDefinition_allColumnTypes() {
        val columns = listOf(
            ColumnDefinition("col_text", ColumnType.TEXT, true, null),
            ColumnDefinition("col_int", ColumnType.INTEGER, true, null),
            ColumnDefinition("col_bigint", ColumnType.BIGINT, true, null),
            ColumnDefinition("col_decimal", ColumnType.DECIMAL, true, null),
            ColumnDefinition("col_boolean", ColumnType.BOOLEAN, true, null),
            ColumnDefinition("col_date", ColumnType.DATE, true, null),
            ColumnDefinition("col_timestamp", ColumnType.TIMESTAMP, true, null),
            ColumnDefinition("col_blob", ColumnType.BLOB, true, null),
        )

        val tableDef = TableDefinition(
            name = "type_test",
            columns = columns,
            primaryKey = null,
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )

        assertEquals(8, tableDef.columns.size)
        assertEquals(ColumnType.TEXT, tableDef.columns[0].type)
        assertEquals(ColumnType.INTEGER, tableDef.columns[1].type)
        assertEquals(ColumnType.BLOB, tableDef.columns[7].type)
    }
}
