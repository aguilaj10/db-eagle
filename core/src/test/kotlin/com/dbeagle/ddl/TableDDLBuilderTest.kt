package com.dbeagle.ddl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TableDDLBuilderTest {

    @Test
    fun `buildCreateTable with single column`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
            ),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertEquals("""CREATE TABLE "users" ("id" INTEGER NOT NULL)""", ddl)
    }

    @Test
    fun `buildCreateTable with multiple columns and types`() {
        val table = TableDefinition(
            name = "products",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("name", ColumnType.TEXT, nullable = false),
                ColumnDefinition("price", ColumnType.DECIMAL, nullable = true),
                ColumnDefinition("created_at", ColumnType.TIMESTAMP, nullable = false),
            ),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains(""""id" INTEGER NOT NULL"""))
        assertTrue(ddl.contains(""""name" TEXT NOT NULL"""))
        assertTrue(ddl.contains(""""price" DECIMAL"""))
        assertTrue(ddl.contains(""""created_at" TIMESTAMP NOT NULL"""))
    }

    @Test
    fun `buildCreateTable with primary key`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("email", ColumnType.TEXT, nullable = false),
            ),
            primaryKey = listOf("id"),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains("""PRIMARY KEY ("id")"""))
    }

    @Test
    fun `buildCreateTable with composite primary key`() {
        val table = TableDefinition(
            name = "order_items",
            columns = listOf(
                ColumnDefinition("order_id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("product_id", ColumnType.INTEGER, nullable = false),
            ),
            primaryKey = listOf("order_id", "product_id"),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains("""PRIMARY KEY ("order_id", "product_id")"""))
    }

    @Test
    fun `buildCreateTable with foreign key`() {
        val table = TableDefinition(
            name = "orders",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("user_id", ColumnType.INTEGER, nullable = false),
            ),
            foreignKeys = listOf(
                ForeignKeyDefinition(
                    name = "fk_user",
                    columns = listOf("user_id"),
                    refTable = "users",
                    refColumns = listOf("id"),
                    onDelete = "CASCADE",
                    onUpdate = "RESTRICT",
                ),
            ),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains("""CONSTRAINT "fk_user" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON DELETE CASCADE ON UPDATE RESTRICT"""))
    }

    @Test
    fun `buildCreateTable with unnamed foreign key`() {
        val table = TableDefinition(
            name = "orders",
            columns = listOf(
                ColumnDefinition("user_id", ColumnType.INTEGER, nullable = false),
            ),
            foreignKeys = listOf(
                ForeignKeyDefinition(
                    name = null,
                    columns = listOf("user_id"),
                    refTable = "users",
                    refColumns = listOf("id"),
                ),
            ),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains("""FOREIGN KEY ("user_id") REFERENCES "users" ("id")"""))
        assertTrue(!ddl.contains("CONSTRAINT"))
    }

    @Test
    fun `buildCreateTable with unique constraint`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("email", ColumnType.TEXT, nullable = false),
            ),
            uniqueConstraints = listOf(listOf("email")),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains("""UNIQUE ("email")"""))
    }

    @Test
    fun `buildCreateTable with composite unique constraint`() {
        val table = TableDefinition(
            name = "users",
            columns = listOf(
                ColumnDefinition("first_name", ColumnType.TEXT, nullable = false),
                ColumnDefinition("last_name", ColumnType.TEXT, nullable = false),
                ColumnDefinition("email", ColumnType.TEXT, nullable = false),
            ),
            uniqueConstraints = listOf(listOf("first_name", "last_name")),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains("""UNIQUE ("first_name", "last_name")"""))
    }

    @Test
    fun `buildCreateTable with default values`() {
        val table = TableDefinition(
            name = "products",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("status", ColumnType.TEXT, nullable = false, defaultValue = "'active'"),
                ColumnDefinition("quantity", ColumnType.INTEGER, nullable = false, defaultValue = "0"),
            ),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains(""""status" TEXT NOT NULL DEFAULT 'active'"""))
        assertTrue(ddl.contains(""""quantity" INTEGER NOT NULL DEFAULT 0"""))
    }

    @Test
    fun `buildCreateTable quotes identifiers with special characters`() {
        val table = TableDefinition(
            name = "my\"table",
            columns = listOf(
                ColumnDefinition("col\"name", ColumnType.TEXT, nullable = false),
            ),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains(""""my""table""""))
        assertTrue(ddl.contains(""""col""name""""))
    }

    @Test
    fun `buildAlterTableAddColumn with nullable column`() {
        val column = ColumnDefinition("age", ColumnType.INTEGER, nullable = true)

        val ddl = TableDDLBuilder.buildAlterTableAddColumn(MockPostgreSQLDialect, "users", column)

        assertEquals("""ALTER TABLE "users" ADD COLUMN "age" INTEGER""", ddl)
    }

    @Test
    fun `buildAlterTableAddColumn with not null column`() {
        val column = ColumnDefinition("email", ColumnType.TEXT, nullable = false)

        val ddl = TableDDLBuilder.buildAlterTableAddColumn(MockPostgreSQLDialect, "users", column)

        assertEquals("""ALTER TABLE "users" ADD COLUMN "email" TEXT NOT NULL""", ddl)
    }

    @Test
    fun `buildAlterTableAddColumn with default value`() {
        val column = ColumnDefinition("status", ColumnType.TEXT, nullable = false, defaultValue = "'pending'")

        val ddl = TableDDLBuilder.buildAlterTableAddColumn(MockPostgreSQLDialect, "orders", column)

        assertEquals("""ALTER TABLE "orders" ADD COLUMN "status" TEXT NOT NULL DEFAULT 'pending'""", ddl)
    }

    @Test
    fun `buildAlterTableDropColumn when supported`() {
        val ddl = TableDDLBuilder.buildAlterTableDropColumn(MockPostgreSQLDialect, "users", "old_column")

        assertEquals("""ALTER TABLE "users" DROP COLUMN "old_column"""", ddl)
    }

    @Test
    fun `buildAlterTableDropColumn throws when not supported`() {
        assertFailsWith<UnsupportedOperationException> {
            TableDDLBuilder.buildAlterTableDropColumn(MockSQLiteDialect, "users", "old_column")
        }
    }

    @Test
    fun `buildAlterTableAddConstraint with primary key`() {
        val constraint = ConstraintDefinition.PrimaryKey(listOf("id"))

        val ddl = TableDDLBuilder.buildAlterTableAddConstraint(MockPostgreSQLDialect, "users", constraint)

        assertEquals("""ALTER TABLE "users" ADD PRIMARY KEY ("id")""", ddl)
    }

    @Test
    fun `buildAlterTableAddConstraint with composite primary key`() {
        val constraint = ConstraintDefinition.PrimaryKey(listOf("order_id", "product_id"))

        val ddl = TableDDLBuilder.buildAlterTableAddConstraint(MockPostgreSQLDialect, "order_items", constraint)

        assertEquals("""ALTER TABLE "order_items" ADD PRIMARY KEY ("order_id", "product_id")""", ddl)
    }

    @Test
    fun `buildAlterTableAddConstraint with named foreign key`() {
        val fkDef = ForeignKeyDefinition(
            name = "fk_user",
            columns = listOf("user_id"),
            refTable = "users",
            refColumns = listOf("id"),
            onDelete = "CASCADE",
        )
        val constraint = ConstraintDefinition.ForeignKey(fkDef)

        val ddl = TableDDLBuilder.buildAlterTableAddConstraint(MockPostgreSQLDialect, "orders", constraint)

        assertEquals(
            """ALTER TABLE "orders" ADD CONSTRAINT "fk_user" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON DELETE CASCADE""",
            ddl,
        )
    }

    @Test
    fun `buildAlterTableAddConstraint with unnamed foreign key`() {
        val fkDef = ForeignKeyDefinition(
            name = null,
            columns = listOf("user_id"),
            refTable = "users",
            refColumns = listOf("id"),
        )
        val constraint = ConstraintDefinition.ForeignKey(fkDef)

        val ddl = TableDDLBuilder.buildAlterTableAddConstraint(MockPostgreSQLDialect, "orders", constraint)

        assertEquals(
            """ALTER TABLE "orders" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id")""",
            ddl,
        )
    }

    @Test
    fun `buildAlterTableAddConstraint with named unique constraint`() {
        val constraint = ConstraintDefinition.Unique("uk_email", listOf("email"))

        val ddl = TableDDLBuilder.buildAlterTableAddConstraint(MockPostgreSQLDialect, "users", constraint)

        assertEquals("""ALTER TABLE "users" ADD CONSTRAINT "uk_email" UNIQUE ("email")""", ddl)
    }

    @Test
    fun `buildAlterTableAddConstraint with unnamed unique constraint`() {
        val constraint = ConstraintDefinition.Unique(null, listOf("email"))

        val ddl = TableDDLBuilder.buildAlterTableAddConstraint(MockPostgreSQLDialect, "users", constraint)

        assertEquals("""ALTER TABLE "users" ADD UNIQUE ("email")""", ddl)
    }

    @Test
    fun `buildAlterTableDropConstraint`() {
        val ddl = TableDDLBuilder.buildAlterTableDropConstraint(MockPostgreSQLDialect, "users", "fk_old")

        assertEquals("""ALTER TABLE "users" DROP CONSTRAINT "fk_old"""", ddl)
    }

    @Test
    fun `buildDropTable with IF EXISTS`() {
        val ddl = TableDDLBuilder.buildDropTable(MockPostgreSQLDialect, "old_table", cascade = false)

        assertEquals("""DROP TABLE IF EXISTS "old_table"""", ddl)
    }

    @Test
    fun `buildDropTable with CASCADE`() {
        val ddl = TableDDLBuilder.buildDropTable(MockPostgreSQLDialect, "old_table", cascade = true)

        assertEquals("""DROP TABLE IF EXISTS "old_table" CASCADE""", ddl)
    }

    @Test
    fun `buildDropTable without IF EXISTS when not supported`() {
        val ddl = TableDDLBuilder.buildDropTable(MockSQLiteDialect, "old_table", cascade = false)

        assertEquals("""DROP TABLE "old_table"""", ddl)
    }

    @Test
    fun `buildCreateTable with all features combined`() {
        val table = TableDefinition(
            name = "orders",
            columns = listOf(
                ColumnDefinition("id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("user_id", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("status", ColumnType.TEXT, nullable = false, defaultValue = "'pending'"),
                ColumnDefinition("created_at", ColumnType.TIMESTAMP, nullable = false),
            ),
            primaryKey = listOf("id"),
            foreignKeys = listOf(
                ForeignKeyDefinition(
                    name = "fk_user",
                    columns = listOf("user_id"),
                    refTable = "users",
                    refColumns = listOf("id"),
                    onDelete = "CASCADE",
                ),
            ),
            uniqueConstraints = listOf(listOf("user_id", "created_at")),
        )

        val ddl = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)

        assertTrue(ddl.contains(""""id" INTEGER NOT NULL"""))
        assertTrue(ddl.contains(""""status" TEXT NOT NULL DEFAULT 'pending'"""))
        assertTrue(ddl.contains("""PRIMARY KEY ("id")"""))
        assertTrue(ddl.contains("""UNIQUE ("user_id", "created_at")"""))
        assertTrue(ddl.contains("""CONSTRAINT "fk_user" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON DELETE CASCADE"""))
    }

    @Test
    fun `buildCreateTable respects dialect type mapping`() {
        val table = TableDefinition(
            name = "test",
            columns = listOf(
                ColumnDefinition("col_text", ColumnType.TEXT, nullable = false),
                ColumnDefinition("col_int", ColumnType.INTEGER, nullable = false),
                ColumnDefinition("col_bigint", ColumnType.BIGINT, nullable = false),
            ),
        )

        val ddlPg = TableDDLBuilder.buildCreateTable(MockPostgreSQLDialect, table)
        val ddlSqlite = TableDDLBuilder.buildCreateTable(MockSQLiteDialect, table)

        assertTrue(ddlPg.contains(""""col_text" TEXT"""))
        assertTrue(ddlSqlite.contains(""""col_text" TEXT"""))
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
