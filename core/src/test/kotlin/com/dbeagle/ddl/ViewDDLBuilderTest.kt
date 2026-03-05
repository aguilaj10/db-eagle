package com.dbeagle.ddl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewDDLBuilderTest {

    @Test
    fun `buildCreateView generates basic CREATE VIEW statement`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "active_users",
            selectQuery = "SELECT * FROM users WHERE active = true",
        )

        assertEquals(
            """CREATE VIEW "active_users" AS SELECT * FROM users WHERE active = true""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView with OR REPLACE flag`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "active_users",
            selectQuery = "SELECT * FROM users WHERE active = true",
            orReplace = true,
        )

        assertEquals(
            """CREATE OR REPLACE VIEW "active_users" AS SELECT * FROM users WHERE active = true""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView without OR REPLACE flag`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "active_users",
            selectQuery = "SELECT * FROM users WHERE active = true",
            orReplace = false,
        )

        assertEquals(
            """CREATE VIEW "active_users" AS SELECT * FROM users WHERE active = true""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView with schema prefix`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "active_users",
            selectQuery = "SELECT * FROM users WHERE active = true",
            schema = "public",
        )

        assertEquals(
            """CREATE VIEW "public"."active_users" AS SELECT * FROM users WHERE active = true""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView with schema and OR REPLACE`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "active_users",
            selectQuery = "SELECT * FROM users WHERE active = true",
            schema = "analytics",
            orReplace = true,
        )

        assertEquals(
            """CREATE OR REPLACE VIEW "analytics"."active_users" AS SELECT * FROM users WHERE active = true""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView quotes identifiers with special characters`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "view\"name",
            selectQuery = "SELECT * FROM users",
            schema = "schema\"name",
        )

        assertTrue(ddl.contains(""""schema""name"."view""name""""))
    }

    @Test
    fun `buildCreateView with complex SELECT query`() {
        val selectQuery = """
            SELECT u.id, u.name, COUNT(o.id) AS order_count
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            WHERE u.active = true
            GROUP BY u.id, u.name
            HAVING COUNT(o.id) > 5
        """.trimIndent()

        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "high_volume_users",
            selectQuery = selectQuery,
        )

        assertTrue(ddl.startsWith("""CREATE VIEW "high_volume_users" AS """))
        assertTrue(ddl.contains(selectQuery))
    }

    @Test
    fun `buildCreateView SQLite dialect`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockSQLiteDialect,
            name = "test_view",
            selectQuery = "SELECT * FROM test_table",
        )

        assertEquals(
            """CREATE VIEW "test_view" AS SELECT * FROM test_table""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView PostgreSQL dialect`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "test_view",
            selectQuery = "SELECT * FROM test_table",
        )

        assertEquals(
            """CREATE VIEW "test_view" AS SELECT * FROM test_table""",
            ddl,
        )
    }

    @Test
    fun `buildDropView generates basic DROP VIEW statement`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
        )

        assertEquals(
            """DROP VIEW IF EXISTS "old_view"""",
            ddl,
        )
    }

    @Test
    fun `buildDropView with IF EXISTS flag when supported`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
            ifExists = true,
        )

        assertEquals(
            """DROP VIEW IF EXISTS "old_view"""",
            ddl,
        )
    }

    @Test
    fun `buildDropView without IF EXISTS flag`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
            ifExists = false,
        )

        assertEquals(
            """DROP VIEW "old_view"""",
            ddl,
        )
    }

    @Test
    fun `buildDropView without IF EXISTS when not supported by dialect`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockSQLiteDialect,
            name = "old_view",
            ifExists = true,
        )

        assertEquals(
            """DROP VIEW "old_view"""",
            ddl,
        )
    }

    @Test
    fun `buildDropView with schema prefix`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
            schema = "public",
        )

        assertEquals(
            """DROP VIEW IF EXISTS "public"."old_view"""",
            ddl,
        )
    }

    @Test
    fun `buildDropView with schema and without IF EXISTS`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
            schema = "analytics",
            ifExists = false,
        )

        assertEquals(
            """DROP VIEW "analytics"."old_view"""",
            ddl,
        )
    }

    @Test
    fun `buildDropView with CASCADE flag`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
            cascade = true,
        )

        assertEquals(
            """DROP VIEW IF EXISTS "old_view" CASCADE""",
            ddl,
        )
    }

    @Test
    fun `buildDropView with CASCADE and without IF EXISTS`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
            ifExists = false,
            cascade = true,
        )

        assertEquals(
            """DROP VIEW "old_view" CASCADE""",
            ddl,
        )
    }

    @Test
    fun `buildDropView with schema, IF EXISTS, and CASCADE`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "old_view",
            schema = "public",
            ifExists = true,
            cascade = true,
        )

        assertEquals(
            """DROP VIEW IF EXISTS "public"."old_view" CASCADE""",
            ddl,
        )
    }

    @Test
    fun `buildDropView quotes identifiers with special characters`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "view\"name",
            schema = "schema\"name",
        )

        assertTrue(ddl.contains(""""schema""name"."view""name""""))
    }

    @Test
    fun `buildDropView SQLite dialect`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockSQLiteDialect,
            name = "test_view",
        )

        assertEquals(
            """DROP VIEW "test_view"""",
            ddl,
        )
    }

    @Test
    fun `buildDropView PostgreSQL dialect with all features`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "test_view",
            schema = "test_schema",
            ifExists = true,
            cascade = true,
        )

        assertEquals(
            """DROP VIEW IF EXISTS "test_schema"."test_view" CASCADE""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView with empty schema uses unqualified name`() {
        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "simple_view",
            selectQuery = "SELECT 1",
            schema = null,
        )

        assertEquals(
            """CREATE VIEW "simple_view" AS SELECT 1""",
            ddl,
        )
    }

    @Test
    fun `buildDropView with empty schema uses unqualified name`() {
        val ddl = ViewDDLBuilder.buildDropView(
            dialect = MockPostgreSQLDialect,
            name = "simple_view",
            schema = null,
        )

        assertEquals(
            """DROP VIEW IF EXISTS "simple_view"""",
            ddl,
        )
    }

    @Test
    fun `buildCreateView handles multiline SELECT query`() {
        val selectQuery = """
            SELECT 
                id, 
                name, 
                email
            FROM users
            WHERE active = true
        """.trimIndent()

        val ddl = ViewDDLBuilder.buildCreateView(
            dialect = MockPostgreSQLDialect,
            name = "formatted_view",
            selectQuery = selectQuery,
        )

        assertTrue(ddl.startsWith("""CREATE VIEW "formatted_view" AS """))
        assertTrue(ddl.contains(selectQuery))
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
