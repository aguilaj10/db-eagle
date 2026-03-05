package com.dbeagle.driver

import com.dbeagle.model.SequenceMetadata
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OracleDriverTest {
    private val driver = OracleDriver()

    @Test
    fun testGetNameReturnsOracle() {
        val name = driver.getName()
        assertEquals("Oracle", name)
    }

    @Test
    fun testGetCapabilitiesIncludesSequences() {
        val capabilities = driver.getCapabilities()

        assertTrue(
            capabilities.contains(DatabaseCapability.Sequences),
            "Expected Oracle driver to support sequences",
        )
        assertTrue(capabilities.contains(DatabaseCapability.Transactions))
        assertTrue(capabilities.contains(DatabaseCapability.PreparedStatements))
        assertTrue(capabilities.contains(DatabaseCapability.ForeignKeys))
        assertTrue(capabilities.contains(DatabaseCapability.Schemas))
        assertTrue(capabilities.contains(DatabaseCapability.Views))
        assertTrue(capabilities.contains(DatabaseCapability.Indexes))
    }

    @Test
    fun testGetSequencesReturnsEmptyListWhenNotConnected() = runBlocking {
        val sequences = driver.getSequences()
        assertTrue(sequences.isEmpty(), "Expected empty list when not connected")
    }

    @Test
    fun testSequenceMetadataStructure() {
        val sequence = SequenceMetadata(
            name = "SEQ_TEST_ID",
            schema = "TESTSCHEMA",
            startValue = 1L,
            increment = 1L,
            minValue = 1L,
            maxValue = 9999999999L,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null,
        )

        assertEquals("SEQ_TEST_ID", sequence.name)
        assertEquals("TESTSCHEMA", sequence.schema)
        assertEquals(1L, sequence.startValue)
        assertEquals(1L, sequence.increment)
        assertEquals(1L, sequence.minValue)
        assertEquals(9999999999L, sequence.maxValue)
        assertEquals(false, sequence.cycle)
        assertEquals(null, sequence.ownedByTable)
        assertEquals(null, sequence.ownedByColumn)
    }

    @Test
    fun testSequenceMetadataWithCycle() {
        val sequence = SequenceMetadata(
            name = "SEQ_CYCLIC",
            schema = "TESTSCHEMA",
            startValue = 500L,
            increment = 5L,
            minValue = 1L,
            maxValue = 1000L,
            cycle = true,
            ownedByTable = null,
            ownedByColumn = null,
        )

        assertTrue(sequence.cycle, "Expected cycle to be true")
        assertEquals(5L, sequence.increment)
        assertEquals(500L, sequence.startValue)
    }

    @Test
    fun testSequenceMetadataWithLargeValues() {
        val sequence = SequenceMetadata(
            name = "SEQ_LARGE",
            schema = "TESTSCHEMA",
            startValue = 999999999999L,
            increment = 1000L,
            minValue = Long.MIN_VALUE,
            maxValue = Long.MAX_VALUE,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null,
        )

        assertEquals(Long.MIN_VALUE, sequence.minValue)
        assertEquals(Long.MAX_VALUE, sequence.maxValue)
        assertEquals(1000L, sequence.increment)
        assertEquals(999999999999L, sequence.startValue)
    }

    @Test
    fun testSequenceMetadataSorting() {
        val sequences = listOf(
            SequenceMetadata("SEQ_ORDERS_ID", "SCHEMA", 1, 1, 1, 999, false, null, null),
            SequenceMetadata("SEQ_USERS_ID", "SCHEMA", 1, 1, 1, 999, false, null, null),
            SequenceMetadata("SEQ_AUDIT_ID", "SCHEMA", 1, 1, 1, 999, false, null, null),
        )

        val sorted = sequences.sortedBy { it.name }

        assertEquals("SEQ_AUDIT_ID", sorted[0].name)
        assertEquals("SEQ_ORDERS_ID", sorted[1].name)
        assertEquals("SEQ_USERS_ID", sorted[2].name)
    }
}
