package com.dbeagle.ui.dialogs

import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.ValidationResult
import com.dbeagle.model.SequenceMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for SequenceEditorDialog validation logic and data flow.
 * These tests focus on the business logic, not UI rendering.
 */
class SequenceEditorDialogTest {

    @Test
    fun testSequenceNameValidation_valid() {
        val validName = "my_sequence"
        val result = DDLValidator.validateIdentifier(validName)
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun testSequenceNameValidation_empty() {
        val emptyName = ""
        val result = DDLValidator.validateIdentifier(emptyName)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun testSequenceNameValidation_invalidChars() {
        val invalidName = "my-sequence"
        val result = DDLValidator.validateIdentifier(invalidName)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { it.contains("invalid characters") })
    }

    @Test
    fun testSequenceNameValidation_sqlInjection() {
        val maliciousName = "DROP TABLE"
        val result = DDLValidator.validateIdentifier(maliciousName)
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { it.contains("reserved keyword") || it.contains("invalid") })
    }

    @Test
    fun testSequenceMetadataCreation_defaults() {
        val sequence = SequenceMetadata(
            name = "test_seq",
            schema = "public",
            startValue = 1L,
            increment = 1L,
            minValue = 1L,
            maxValue = Long.MAX_VALUE,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null,
        )

        assertEquals("test_seq", sequence.name)
        assertEquals("public", sequence.schema)
        assertEquals(1L, sequence.startValue)
        assertEquals(1L, sequence.increment)
        assertEquals(false, sequence.cycle)
    }

    @Test
    fun testSequenceMetadataCreation_customValues() {
        val sequence = SequenceMetadata(
            name = "custom_seq",
            schema = "public",
            startValue = 100L,
            increment = 5L,
            minValue = 1L,
            maxValue = 1000L,
            cycle = true,
            ownedByTable = null,
            ownedByColumn = null,
        )

        assertEquals(100L, sequence.startValue)
        assertEquals(5L, sequence.increment)
        assertEquals(1000L, sequence.maxValue)
        assertEquals(true, sequence.cycle)
    }

    @Test
    fun testSequenceMetadataCreation_ownedSequence() {
        val sequence = SequenceMetadata(
            name = "users_id_seq",
            schema = "public",
            startValue = 1L,
            increment = 1L,
            minValue = 1L,
            maxValue = Long.MAX_VALUE,
            cycle = false,
            ownedByTable = "users",
            ownedByColumn = "id",
        )

        assertEquals("users", sequence.ownedByTable)
        assertEquals("id", sequence.ownedByColumn)
    }

    @Test
    fun testSequenceMetadataCallback_createMode() {
        // Simulate dialog create flow
        val name = "new_sequence"
        val startValue = "10"
        val increment = "2"
        val minValue = ""
        val maxValue = ""
        val cycle = false

        // Validate name
        val nameValidation = DDLValidator.validateIdentifier(name)
        assertIs<ValidationResult.Valid>(nameValidation)

        // Create sequence (simulating onSave callback)
        val sequence = SequenceMetadata(
            name = name,
            schema = "public",
            startValue = startValue.toLongOrNull() ?: 1L,
            increment = increment.toLongOrNull() ?: 1L,
            minValue = minValue.toLongOrNull() ?: 1L,
            maxValue = maxValue.toLongOrNull() ?: Long.MAX_VALUE,
            cycle = cycle,
            ownedByTable = null,
            ownedByColumn = null,
        )

        assertEquals("new_sequence", sequence.name)
        assertEquals(10L, sequence.startValue)
        assertEquals(2L, sequence.increment)
        assertEquals(1L, sequence.minValue)
        assertEquals(Long.MAX_VALUE, sequence.maxValue)
        assertEquals(false, sequence.cycle)
    }

    @Test
    fun testSequenceMetadataCallback_editMode() {
        // Simulate existing sequence
        val existingSequence = SequenceMetadata(
            name = "existing_seq",
            schema = "public",
            startValue = 1L,
            increment = 1L,
            minValue = 1L,
            maxValue = Long.MAX_VALUE,
            cycle = false,
            ownedByTable = null,
            ownedByColumn = null,
        )

        // Simulate edit: change increment and add max value
        val updatedIncrement = "5"
        val updatedMaxValue = "10000"

        val updatedSequence = SequenceMetadata(
            name = existingSequence.name,
            schema = existingSequence.schema,
            startValue = existingSequence.startValue,
            increment = updatedIncrement.toLongOrNull() ?: existingSequence.increment,
            minValue = existingSequence.minValue,
            maxValue = updatedMaxValue.toLongOrNull() ?: existingSequence.maxValue,
            cycle = existingSequence.cycle,
            ownedByTable = existingSequence.ownedByTable,
            ownedByColumn = existingSequence.ownedByColumn,
        )

        assertEquals("existing_seq", updatedSequence.name)
        assertEquals(5L, updatedSequence.increment)
        assertEquals(10000L, updatedSequence.maxValue)
    }

    @Test
    fun testNumericValueParsing_invalidInput() {
        // Test that invalid numeric inputs default to fallback values
        val invalidStart = "not_a_number"
        val invalidIncrement = "abc"

        val startValue = invalidStart.toLongOrNull() ?: 1L
        val increment = invalidIncrement.toLongOrNull() ?: 1L

        assertEquals(1L, startValue)
        assertEquals(1L, increment)
    }

    @Test
    fun testNumericValueParsing_negativeValues() {
        // Test that negative values are handled correctly
        val negativeStart = "-100"
        val negativeIncrement = "-5"

        val startValue = negativeStart.toLongOrNull() ?: 1L
        val increment = negativeIncrement.toLongOrNull() ?: 1L

        assertEquals(-100L, startValue)
        assertEquals(-5L, increment)
    }
}
