package com.dbeagle.ddl

import com.dbeagle.model.SequenceMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SequenceDDLBuilderTest {

    @Test
    fun `buildCreateSequence generates valid PostgreSQL DDL`() {
        val sequence = SequenceMetadata(
            name = "order_id_seq",
            schema = "public",
            startValue = 1,
            increment = 1,
            minValue = 1,
            maxValue = 9223372036854775807,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null
        )

        val ddl = SequenceDDLBuilder.buildCreateSequence(MockPostgreSQLDialect, sequence)

        assertEquals(
            """CREATE SEQUENCE "order_id_seq" START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 NO CYCLE""",
            ddl
        )
    }

    @Test
    fun `buildCreateSequence with CYCLE enabled`() {
        val sequence = SequenceMetadata(
            name = "cycle_seq",
            schema = "public",
            startValue = 1,
            increment = 1,
            minValue = 1,
            maxValue = 100,
            cycle = true,
            ownedByTable = null,
            ownedByColumn = null
        )

        val ddl = SequenceDDLBuilder.buildCreateSequence(MockPostgreSQLDialect, sequence)

        assertTrue(ddl.endsWith("CYCLE"))
    }

    @Test
    fun `buildCreateSequence quotes identifier with special characters`() {
        val sequence = SequenceMetadata(
            name = "order\"seq",
            schema = "public",
            startValue = 1,
            increment = 1,
            minValue = 1,
            maxValue = 9223372036854775807,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null
        )

        val ddl = SequenceDDLBuilder.buildCreateSequence(MockPostgreSQLDialect, sequence)

        assertTrue(ddl.contains(""""order""seq""""))
    }

    @Test
    fun `buildAlterSequence with all changes`() {
        val changes = SequenceChanges(
            increment = 2,
            minValue = 10,
            maxValue = 1000,
            restart = 50
        )

        val ddl = SequenceDDLBuilder.buildAlterSequence(MockPostgreSQLDialect, "my_seq", changes)

        assertEquals(
            """ALTER SEQUENCE "my_seq" INCREMENT BY 2 MINVALUE 10 MAXVALUE 1000 RESTART WITH 50""",
            ddl
        )
    }

    @Test
    fun `buildAlterSequence with partial changes`() {
        val changes = SequenceChanges(
            increment = 5,
            restart = 100
        )

        val ddl = SequenceDDLBuilder.buildAlterSequence(MockPostgreSQLDialect, "my_seq", changes)

        assertEquals(
            """ALTER SEQUENCE "my_seq" INCREMENT BY 5 RESTART WITH 100""",
            ddl
        )
    }

    @Test
    fun `buildAlterSequence with single change`() {
        val changes = SequenceChanges(minValue = 0)

        val ddl = SequenceDDLBuilder.buildAlterSequence(MockPostgreSQLDialect, "my_seq", changes)

        assertEquals(
            """ALTER SEQUENCE "my_seq" MINVALUE 0""",
            ddl
        )
    }

    @Test
    fun `buildDropSequence with IF EXISTS when supported`() {
        val ddl = SequenceDDLBuilder.buildDropSequence(MockPostgreSQLDialect, "old_seq", ifExists = true)

        assertEquals("""DROP SEQUENCE IF EXISTS "old_seq"""", ddl)
    }

    @Test
    fun `buildDropSequence without IF EXISTS when not supported`() {
        val ddl = SequenceDDLBuilder.buildDropSequence(MockSQLiteDialect, "old_seq", ifExists = true)

        assertEquals("""DROP SEQUENCE "old_seq"""", ddl)
    }

    @Test
    fun `buildDropSequence without IF EXISTS flag`() {
        val ddl = SequenceDDLBuilder.buildDropSequence(MockPostgreSQLDialect, "old_seq", ifExists = false)

        assertEquals("""DROP SEQUENCE "old_seq"""", ddl)
    }

    @Test
    fun `buildCreateSequence with custom increment`() {
        val sequence = SequenceMetadata(
            name = "custom_seq",
            schema = "public",
            startValue = 100,
            increment = 10,
            minValue = 100,
            maxValue = 10000,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null
        )

        val ddl = SequenceDDLBuilder.buildCreateSequence(MockPostgreSQLDialect, sequence)

        assertTrue(ddl.contains("START WITH 100"))
        assertTrue(ddl.contains("INCREMENT BY 10"))
    }

    @Test
    fun `buildCreateSequence generates SQL even when dialect does not support sequences`() {
        val sequence = SequenceMetadata(
            name = "seq",
            schema = "main",
            startValue = 1,
            increment = 1,
            minValue = 1,
            maxValue = 9223372036854775807,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null
        )

        val ddl = SequenceDDLBuilder.buildCreateSequence(MockSQLiteDialect, sequence)

        assertTrue(ddl.startsWith("CREATE SEQUENCE"))
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
