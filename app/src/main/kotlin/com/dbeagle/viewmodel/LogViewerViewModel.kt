package com.dbeagle.viewmodel

import com.dbeagle.logging.QueryLogEntry
import com.dbeagle.logging.QueryLogService
import com.dbeagle.logging.QueryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Filter options for query logs.
 */
enum class LogFilter {
    ALL,
    SUCCESS,
    ERROR,
}

/**
 * UI state for the Log Viewer screen.
 *
 * @property logs All query log entries loaded from QueryLogService
 * @property filter Current filter applied to the logs
 * @property showClearDialog Whether to show the clear confirmation dialog
 */
data class LogViewerUiState(
    val logs: List<QueryLogEntry> = emptyList(),
    val filter: LogFilter = LogFilter.ALL,
    val showClearDialog: Boolean = false,
)

/**
 * ViewModel for the Log Viewer screen.
 *
 * Manages query log display, filtering, and clearing operations.
 * Wraps QueryLogService with StateFlow-based reactive state.
 *
 * Usage pattern:
 * ```
 * val viewModel = LogViewerViewModel()
 * val uiState by viewModel.uiState.collectAsState()
 * val filteredLogs = viewModel.filteredLogs
 * ```
 */
class LogViewerViewModel : BaseViewModel() {

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    /**
     * Returns logs filtered by the current filter setting.
     * Filters are applied on-demand to avoid unnecessary state updates.
     */
    val filteredLogs: List<QueryLogEntry>
        get() = when (_uiState.value.filter) {
            LogFilter.ALL -> _uiState.value.logs
            LogFilter.SUCCESS -> _uiState.value.logs.filter { it.status == QueryStatus.SUCCESS }
            LogFilter.ERROR -> _uiState.value.logs.filter { it.status == QueryStatus.ERROR }
        }

    init {
        refreshLogs()
    }

    /**
     * Reload logs from QueryLogService.
     * Call this when logs may have been updated externally.
     */
    fun refreshLogs() {
        val logs = QueryLogService.getLogs()
        updateStateFlow(_uiState) { it.copy(logs = logs) }
    }

    /**
     * Update the current filter for log display.
     *
     * @param filter The filter to apply (ALL, SUCCESS, or ERROR)
     */
    fun setFilter(filter: LogFilter) {
        updateStateFlow(_uiState) { it.copy(filter = filter) }
    }

    /**
     * Show the clear logs confirmation dialog.
     */
    fun showClearDialog() {
        updateStateFlow(_uiState) { it.copy(showClearDialog = true) }
    }

    /**
     * Hide the clear logs confirmation dialog without clearing.
     */
    fun hideClearDialog() {
        updateStateFlow(_uiState) { it.copy(showClearDialog = false) }
    }

    /**
     * Clear all query logs from QueryLogService and update UI state.
     * Automatically hides the confirmation dialog after clearing.
     */
    fun clearLogs() {
        QueryLogService.clearLogs()
        updateStateFlow(_uiState) { it.copy(logs = emptyList(), showClearDialog = false) }
    }
}
