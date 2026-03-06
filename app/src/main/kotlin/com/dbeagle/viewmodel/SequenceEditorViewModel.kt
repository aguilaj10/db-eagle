package com.dbeagle.viewmodel

import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.ValidationResult
import com.dbeagle.model.SequenceMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for SequenceEditorDialog that manages sequence editing state and business logic.
 *
 * Responsibilities:
 * - Manage sequence definition state (name, startValue, increment, minValue, maxValue, cycle)
 * - Handle all user interactions through event handlers
 * - Validate sequence names before save
 * - Parse numeric input fields with error handling
 * - Maintain separation between business logic and UI
 * - Provide unidirectional data flow through StateFlow
 *
 * Architecture:
 * - Immutable state via data class
 * - Event-driven updates (7 event handler functions)
 * - Centralized validation logic
 * - String-to-Long parsing with validation errors
 *
 * Usage:
 * ```
 * val viewModel = SequenceEditorViewModel(existingSequence)
 * val uiState by viewModel.uiState.collectAsState()
 *
 * // User enters values
 * viewModel.updateName("my_sequence")
 * viewModel.updateStartValue("100")
 * viewModel.updateIncrement("5")
 *
 * // User saves
 * if (viewModel.validate()) {
 *     onSave(viewModel.buildSequenceMetadata(existingSchema))
 * }
 * ```
 */
class SequenceEditorViewModel(
    private val existingSequence: SequenceMetadata?,
) : BaseViewModel() {

    /**
     * UI state for the Sequence Editor dialog.
     *
     * Contains all state needed to render the UI and perform validation.
     * All fields are immutable to enforce unidirectional data flow.
     *
     * @property name The name of the sequence
     * @property startValue String representation of start value
     * @property increment String representation of increment
     * @property minValue String representation of min value (optional)
     * @property maxValue String representation of max value (optional)
     * @property cycle If true, sequence cycles when reaching limit
     * @property isOwnedSequence True if sequence is owned by a SERIAL column
     * @property isCreateMode True if creating new sequence, false if editing
     * @property isNameValid True if name passes validation
     * @property isFormValid True if all required fields are valid
     * @property validationErrors Set of validation error messages
     */
    data class SequenceEditorUiState(
        val name: String = "",
        val startValue: String = "1",
        val increment: String = "1",
        val minValue: String = "",
        val maxValue: String = "",
        val cycle: Boolean = false,
        val isOwnedSequence: Boolean = false,
        val isCreateMode: Boolean = true,
        val isNameValid: Boolean = false,
        val isFormValid: Boolean = false,
        val validationErrors: Set<String> = emptySet(),
    )

    private val _uiState = MutableStateFlow(
        SequenceEditorUiState(
            name = existingSequence?.name ?: "",
            startValue = existingSequence?.startValue?.toString() ?: "1",
            increment = existingSequence?.increment?.toString() ?: "1",
            minValue = existingSequence?.minValue?.toString() ?: "",
            maxValue = existingSequence?.maxValue?.toString() ?: "",
            cycle = existingSequence?.cycle ?: false,
            isOwnedSequence = existingSequence?.ownedByTable != null,
            isCreateMode = existingSequence == null,
        ).revalidate(),
    )
    val uiState: StateFlow<SequenceEditorUiState> = _uiState.asStateFlow()

    // ========================================
    // EVENT HANDLERS
    // ========================================

    /**
     * Updates the sequence name and revalidates.
     *
     * @param newName The new sequence name
     */
    fun updateName(newName: String) {
        updateStateFlow(_uiState) { state ->
            state.copy(name = newName).revalidate()
        }
    }

    /**
     * Updates the start value and validates numeric input.
     *
     * @param value String representation of start value
     */
    fun updateStartValue(value: String) {
        updateStateFlow(_uiState) { state ->
            val parsed = value.toLongOrNull()
            val newErrors = if (parsed == null && value.isNotEmpty()) {
                state.validationErrors + "Invalid start value"
            } else {
                state.validationErrors - "Invalid start value"
            }
            state.copy(
                startValue = value,
                validationErrors = newErrors,
            ).revalidate()
        }
    }

    /**
     * Updates the increment value and validates numeric input.
     *
     * @param value String representation of increment
     */
    fun updateIncrement(value: String) {
        updateStateFlow(_uiState) { state ->
            val parsed = value.toLongOrNull()
            val newErrors = if (parsed == null && value.isNotEmpty()) {
                state.validationErrors + "Invalid increment value"
            } else {
                state.validationErrors - "Invalid increment value"
            }
            state.copy(
                increment = value,
                validationErrors = newErrors,
            ).revalidate()
        }
    }

    /**
     * Updates the min value and validates numeric input.
     *
     * @param value String representation of min value (empty allowed)
     */
    fun updateMinValue(value: String) {
        updateStateFlow(_uiState) { state ->
            val parsed = value.toLongOrNull()
            val newErrors = if (parsed == null && value.isNotEmpty()) {
                state.validationErrors + "Invalid min value"
            } else {
                state.validationErrors - "Invalid min value"
            }
            state.copy(
                minValue = value,
                validationErrors = newErrors,
            ).revalidate()
        }
    }

    /**
     * Updates the max value and validates numeric input.
     *
     * @param value String representation of max value (empty allowed)
     */
    fun updateMaxValue(value: String) {
        updateStateFlow(_uiState) { state ->
            val parsed = value.toLongOrNull()
            val newErrors = if (parsed == null && value.isNotEmpty()) {
                state.validationErrors + "Invalid max value"
            } else {
                state.validationErrors - "Invalid max value"
            }
            state.copy(
                maxValue = value,
                validationErrors = newErrors,
            ).revalidate()
        }
    }

    /**
     * Toggles the cycle option.
     */
    fun toggleCycle() {
        updateStateFlow(_uiState) { state ->
            state.copy(cycle = !state.cycle)
        }
    }

    /**
     * Validates the current state.
     * Returns true if the form is valid and can be saved.
     *
     * @return True if form is valid, false otherwise
     */
    fun validate(): Boolean = _uiState.value.isFormValid

    /**
     * Builds a SequenceMetadata object from the current state.
     * Should only be called after validate() returns true.
     *
     * @param schema The schema for the sequence (typically "public")
     * @return SequenceMetadata constructed from current UI state
     */
    fun buildSequenceMetadata(schema: String = "public"): SequenceMetadata {
        val state = _uiState.value
        return SequenceMetadata(
            name = state.name,
            schema = schema,
            startValue = state.startValue.toLongOrNull() ?: 1L,
            increment = state.increment.toLongOrNull() ?: 1L,
            minValue = state.minValue.toLongOrNull() ?: 1L,
            maxValue = state.maxValue.toLongOrNull() ?: Long.MAX_VALUE,
            cycle = state.cycle,
            ownedByTable = existingSequence?.ownedByTable,
            ownedByColumn = existingSequence?.ownedByColumn,
        )
    }

    // ========================================
    // PRIVATE HELPER FUNCTIONS
    // ========================================

    /**
     * Validates the current state and updates validation flags.
     * This method is called automatically by other event handlers.
     * Returns the current state with updated validation results.
     *
     * @receiver The current SequenceEditorUiState
     * @return Updated state with validation results
     */
    private fun SequenceEditorUiState.revalidate(): SequenceEditorUiState {
        // Name validation
        val nameValidation = if (name.isNotBlank()) {
            DDLValidator.validateIdentifier(name)
        } else {
            ValidationResult.Invalid(listOf("Sequence name is required"))
        }

        val isNameValid = nameValidation is ValidationResult.Valid

        // Add name validation errors
        val nameErrors = when (nameValidation) {
            is ValidationResult.Invalid -> nameValidation.errors.toSet()
            ValidationResult.Valid -> emptySet()
        }

        // Numeric field validation
        val startValueValid = startValue.isNotEmpty() && startValue.toLongOrNull() != null
        val incrementValid = increment.isNotEmpty() && increment.toLongOrNull() != null

        // Combine all errors
        val allErrors = (validationErrors + nameErrors).filter {
            // Keep numeric errors that are in validationErrors
            // Keep name errors that are in nameErrors
            it in validationErrors || it in nameErrors
        }.toSet()

        // Form is valid if name is valid, required numeric fields are valid, and no validation errors exist
        val isFormValid = isNameValid &&
            startValueValid &&
            incrementValid &&
            !allErrors.any { it.startsWith("Invalid") }

        return copy(
            isNameValid = isNameValid,
            isFormValid = isFormValid,
            validationErrors = allErrors,
        )
    }
}
