package com.dbeagle.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for IndexEditorDialog that manages column loading state.
 *
 * Responsibilities:
 * - Load available columns for selected table
 * - Track loading state for columns
 * - Handle errors during column loading
 * - Maintain separation between business logic and UI
 *
 * Usage:
 * ```
 * val viewModel: IndexEditorViewModel = koinViewModel()
 * val uiState by viewModel.uiState.collectAsState()
 *
 * // When table is selected
 * viewModel.loadColumnsForTable(tableName, getColumnsForTable)
 * ```
 */
class IndexEditorViewModel : BaseViewModel() {

    /**
     * UI state for the Index Editor dialog
     *
     * @property availableColumns List of column names for the selected table
     * @property isLoadingColumns True while fetching columns from database
     * @property errorMessage Error message if column loading fails, null otherwise
     */
    data class IndexEditorUiState(
        val availableColumns: List<String> = emptyList(),
        val isLoadingColumns: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(IndexEditorUiState())
    val uiState: StateFlow<IndexEditorUiState> = _uiState.asStateFlow()

    /**
     * Loads available columns for the specified table.
     *
     * This function:
     * 1. Sets loading state to true
     * 2. Invokes the provided suspend function to fetch columns
     * 3. Updates state with columns or error
     * 4. Clears loading state
     *
     * @param tableName Name of the table to fetch columns for
     * @param getColumnsForTable Suspend function that retrieves columns for a table
     */
    fun loadColumnsForTable(
        tableName: String,
        getColumnsForTable: suspend (String) -> List<String>,
    ) {
        viewModelScope.launch {
            updateStateFlow(_uiState) {
                it.copy(
                    isLoadingColumns = true,
                    errorMessage = null,
                )
            }

            try {
                val columns = getColumnsForTable(tableName)

                _uiState.emit(
                    _uiState.value.copy(
                        availableColumns = columns,
                        isLoadingColumns = false,
                        errorMessage = null,
                    )
                )
            } catch (e: Exception) {
                updateStateFlow(_uiState) {
                    it.copy(
                        availableColumns = emptyList(),
                        isLoadingColumns = false,
                        errorMessage = "Failed to load columns: ${e.message}",
                    )
                }
            }
        }
    }
}
