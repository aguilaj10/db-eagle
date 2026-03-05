package com.dbeagle.ddl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexDDLBuilderTest {

    @Test
    fun `buildCreateIndex with single column`() {
        val index = IndexDefinition(
            name = "idx_users_email",
            tableName = "users",
            columns = listOf("email"),
            unique = false,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)

        assertEquals("""CREATE INDEX "idx_users_email" ON "users" ("email")""", ddl)
    }

    @Test
    fun `buildCreateIndex with multiple columns`() {
        val index = IndexDefinition(
            name = "idx_orders_user_date",
            tableName = "orders",
            columns = listOf("user_id", "created_at"),
            unique = false,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)

        assertEquals("""CREATE INDEX "idx_orders_user_date" ON "orders" ("user_id", "created_at")""", ddl)
    }

    @Test
    fun `buildCreateIndex with UNIQUE flag`() {
        val index = IndexDefinition(
            name = "idx_unique_email",
            tableName = "users",
            columns = listOf("email"),
            unique = true,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)

        assertEquals("""CREATE UNIQUE INDEX "idx_unique_email" ON "users" ("email")""", ddl)
    }

    @Test
    fun `buildCreateIndex quotes identifiers with special characters`() {
        val index = IndexDefinition(
            name = "idx\"special",
            tableName = "my\"table",
            columns = listOf("col\"name"),
            unique = false,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)

        assertTrue(ddl.contains(""""idx""special""""))
        assertTrue(ddl.contains(""""my""table""""))
        assertTrue(ddl.contains(""""col""name""""))
    }

    @Test
    fun `buildCreateIndex with three columns`() {
        val index = IndexDefinition(
            name = "idx_composite",
            tableName = "orders",
            columns = listOf("user_id", "status", "created_at"),
            unique = false,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)

        assertEquals("""CREATE INDEX "idx_composite" ON "orders" ("user_id", "status", "created_at")""", ddl)
    }

    @Test
    fun `buildCreateIndex SQLite dialect`() {
        val index = IndexDefinition(
            name = "idx_test",
            tableName = "test_table",
            columns = listOf("test_col"),
            unique = false,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockSQLiteDialect, index)

        assertEquals("""CREATE INDEX "idx_test" ON "test_table" ("test_col")""", ddl)
    }

    @Test
    fun `buildDropIndex with IF EXISTS when supported`() {
        val ddl = IndexDDLBuilder.buildDropIndex(MockPostgreSQLDialect, "idx_old", tableName = "users", ifExists = true)

        assertEquals("""DROP INDEX IF EXISTS "idx_old"""", ddl)
    }

    @Test
    fun `buildDropIndex without IF EXISTS when not supported`() {
        val ddl = IndexDDLBuilder.buildDropIndex(MockSQLiteDialect, "idx_old", tableName = "users", ifExists = true)

        assertEquals("""DROP INDEX "idx_old"""", ddl)
    }

    @Test
    fun `buildDropIndex without IF EXISTS flag`() {
        val ddl = IndexDDLBuilder.buildDropIndex(MockPostgreSQLDialect, "idx_old", tableName = "users", ifExists = false)

        assertEquals("""DROP INDEX "idx_old"""", ddl)
    }

    @Test
    fun `buildDropIndex quotes identifier with special characters`() {
        val ddl = IndexDDLBuilder.buildDropIndex(MockPostgreSQLDialect, "idx\"special", tableName = null, ifExists = true)

        assertTrue(ddl.contains(""""idx""special""""))
    }

    @Test
    fun `buildDropIndex with null tableName`() {
        val ddl = IndexDDLBuilder.buildDropIndex(MockPostgreSQLDialect, "idx_test", tableName = null, ifExists = true)

        assertEquals("""DROP INDEX IF EXISTS "idx_test"""", ddl)
    }

    @Test
    fun `buildCreateIndex with unique composite index`() {
        val index = IndexDefinition(
            name = "idx_unique_first_last",
            tableName = "users",
            columns = listOf("first_name", "last_name"),
            unique = true,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)

        assertEquals("""CREATE UNIQUE INDEX "idx_unique_first_last" ON "users" ("first_name", "last_name")""", ddl)
    }

    @Test
    fun `buildCreateIndex PostgreSQL vs SQLite produces same syntax`() {
        val index = IndexDefinition(
            name = "idx_test",
            tableName = "test_table",
            columns = listOf("col1", "col2"),
            unique = false,
        )

        val ddlPg = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)
        val ddlSqlite = IndexDDLBuilder.buildCreateIndex(MockSQLiteDialect, index)

        assertEquals(ddlPg, ddlSqlite)
    }

    @Test
    fun `buildDropIndex ignores tableName parameter`() {
        val ddlWithTable = IndexDDLBuilder.buildDropIndex(
            MockPostgreSQLDialect,
            "idx_test",
            tableName = "users",
            ifExists = true,
        )
        val ddlWithoutTable = IndexDDLBuilder.buildDropIndex(
            MockPostgreSQLDialect,
            "idx_test",
            tableName = null,
            ifExists = true,
        )

        assertEquals(ddlWithTable, ddlWithoutTable)
    }

    @Test
    fun `buildCreateIndex handles underscore in names`() {
        val index = IndexDefinition(
            name = "idx_user_email_active",
            tableName = "user_accounts",
            columns = listOf("email_address", "is_active"),
            unique = false,
        )

        val ddl = IndexDDLBuilder.buildCreateIndex(MockPostgreSQLDialect, index)

        assertTrue(ddl.contains(""""idx_user_email_active""""))
        assertTrue(ddl.contains(""""user_accounts""""))
        assertTrue(ddl.contains(""""email_address""""))
        assertTrue(ddl.contains(""""is_active""""))
    }

    private object MockPostgreSQLDialect : DDLDialect {
        override fun quoteIdentifier(name: String): String = """"${name.replace("\"", "\"\"")}""""
        override fun supportsSequences(): Boolean = true
        override fun supportsAlterColumn(): Boolean = true
        override fun supportsDropColumn(): Boolean = true
        override fun supportsIfExists(): Boolean = true
        override fun getTypeName(genericType: ColumnType): String = genericType.name
    }

    private object MockSQLiteDialect : DDLDialect {
        override fun quoteIdentifier(name: String): String = """"${name.replace("\"", "\"\"")}""""
        override fun supportsSequences(): Boolean = false
        override fun supportsAlterColumn(): Boolean = false
        override fun supportsDropColumn(): Boolean = false
        override fun supportsIfExists(): Boolean = false
        override fun getTypeName(genericType: ColumnType): String = genericType.name
    }
}
