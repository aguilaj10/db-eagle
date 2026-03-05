package com.dbeagle.viewmodel

import com.dbeagle.history.QueryHistoryRepository
import com.dbeagle.model.QueryHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: QueryHistoryRepository,
) : BaseViewModel() {

    data class HistoryUiState(
        val entries: List<QueryHistoryEntry> = emptyList(),
        val showClearDialog: Boolean = false,
    )

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllFlow()
                .distinctUntilChanged()
                .collect { entries ->
                    updateStateFlow(_uiState) { it.copy(entries = entries) }
                }
        }
    }

    fun refreshHistory() {
        // No-op: Flow handles updates automatically
    }

    fun showClearDialog() {
        updateStateFlow(_uiState) { it.copy(showClearDialog = true) }
    }

    fun hideClearDialog() {
        updateStateFlow(_uiState) { it.copy(showClearDialog = false) }
    }

    fun clearHistory() {
        repository.clear()
        updateStateFlow(_uiState) { it.copy(entries = emptyList(), showClearDialog = false) }
    }
}
