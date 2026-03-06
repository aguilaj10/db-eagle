package com.dbeagle.viewmodel

import com.dbeagle.ddl.DDLDialect
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.ValidationResult
import com.dbeagle.ddl.ViewDDLBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for ViewEditorDialog that manages view creation state and business logic.
 *
 * Responsibilities:
 * - Manage view definition state (name, schema, query, options)
 * - Handle all user interactions through event handlers
 * - Validate view names before DDL generation
 * - Generate DDL using ViewDDLBuilder
 * - Maintain separation between business logic and UI
 * - Provide unidirectional data flow through StateFlow
 *
 * Architecture:
 * - Immutable state via data class
 * - Event-driven updates (6 event handler functions)
 * - Centralized validation logic
 * - DDL generation on-demand
 *
 * Usage:
 * ```
 * val viewModel = ViewEditorViewModel(dialect)
 * val uiState by viewModel.uiState.collectAsState()
 *
 * // User enters view name
 * viewModel.updateViewName("my_view")
 * viewModel.updateSelectQuery("SELECT * FROM users")
 *
 * // User saves
 * if (uiState.isFormValid && uiState.generatedDdl.isNotEmpty()) {
 *     onSave(uiState.generatedDdl)
 * }
 * ```
 */
class ViewEditorViewModel(
    private val dialect: DDLDialect,
) : BaseViewModel() {

    /**
     * UI state for the View Editor dialog.
     *
     * Contains all state needed to render the UI and perform validation.
     * All fields are immutable to enforce unidirectional data flow.
     *
     * @property viewName The name of the view being created
     * @property schema Optional schema name for the view
     * @property selectQuery The SELECT query defining the view
     * @property orReplace If true, generates CREATE OR REPLACE VIEW
     * @property showPreview If true, shows the DDL preview dialog
     * @property isNameValid True if viewName passes validation
     * @property isQueryValid True if selectQuery is not blank
     * @property isFormValid True if both name and query are valid
     * @property validationErrors List of validation error messages
     * @property generatedDdl The generated DDL statement (empty if form invalid)
     */
    data class ViewEditorUiState(
        val viewName: String = "",
        val schema: String = "",
        val selectQuery: String = "",
        val orReplace: Boolean = false,
        val showPreview: Boolean = false,
        val isNameValid: Boolean = false,
        val isQueryValid: Boolean = false,
        val isFormValid: Boolean = false,
        val validationErrors: List<String> = emptyList(),
        val generatedDdl: String = "",
    )

    private val _uiState = MutableStateFlow(ViewEditorUiState())
    val uiState: StateFlow<ViewEditorUiState> = _uiState.asStateFlow()

    // ========================================
    // EVENT HANDLERS
    // ========================================

    /**
     * Updates the view name and revalidates.
     *
     * @param newName The new view name
     */
    fun updateViewName(newName: String) {
        updateStateFlow(_uiState) { state ->
            state.copy(viewName = newName).revalidate()
        }
    }

    /**
     * Updates the schema and regenerates DDL.
     *
     * @param newSchema The new schema name (can be empty)
     */
    fun updateSchema(newSchema: String) {
        updateStateFlow(_uiState) { state ->
            state.copy(schema = newSchema).revalidate()
        }
    }

    /**
     * Updates the SELECT query and revalidates.
     *
     * @param newQuery The new SELECT query
     */
    fun updateSelectQuery(newQuery: String) {
        updateStateFlow(_uiState) { state ->
            state.copy(selectQuery = newQuery).revalidate()
        }
    }

    /**
     * Toggles the OR REPLACE option.
     *
     * @param enabled If true, uses CREATE OR REPLACE VIEW
     */
    fun toggleOrReplace(enabled: Boolean) {
        updateStateFlow(_uiState) { state ->
            state.copy(orReplace = enabled).revalidate()
        }
    }

    /**
     * Toggles the preview dialog visibility.
     *
     * @param show If true, shows the preview dialog
     */
    fun togglePreview(show: Boolean) {
        updateStateFlow(_uiState) { state ->
            state.copy(showPreview = show)
        }
    }

    /**
     * Validates the current state and generates DDL if valid.
     * This method is called automatically by other event handlers.
     * Returns the current state with updated validation results.
     *
     * @receiver The current ViewEditorUiState
     * @return Updated state with validation results and generated DDL
     */
    private fun ViewEditorUiState.revalidate(): ViewEditorUiState {
        val nameValidation = if (viewName.isNotBlank()) {
            DDLValidator.validateIdentifier(viewName)
        } else {
            ValidationResult.Invalid(listOf("View name is required"))
        }

        val isNameValid = nameValidation is ValidationResult.Valid
        val isQueryValid = selectQuery.isNotBlank()
        val isFormValid = isNameValid && isQueryValid

        val validationErrors = when (nameValidation) {
            is ValidationResult.Invalid -> nameValidation.errors
            ValidationResult.Valid -> emptyList()
        }

        val generatedDdl = if (isFormValid) {
            ViewDDLBuilder.buildCreateView(
                dialect = dialect,
                name = viewName,
                selectQuery = selectQuery,
                schema = schema.ifBlank { null },
                orReplace = orReplace,
            )
        } else {
            ""
        }

        return copy(
            isNameValid = isNameValid,
            isQueryValid = isQueryValid,
            isFormValid = isFormValid,
            validationErrors = validationErrors,
            generatedDdl = generatedDdl,
        )
    }
}
