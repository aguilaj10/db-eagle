package com.dbeagle.viewmodel

import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.QueryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TableDataEditorViewModel : BaseViewModel() {
    data class UiState(
        val isLoading: Boolean = false,
        val columns: List<String> = emptyList(),
        val rows: List<List<String>> = emptyList(),
        val error: String? = null,
        val hasPendingChanges: Boolean = false,
        val currentPage: Int = 0,
        val totalRows: Int = 0,
        val pageSize: Int = 100,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val pendingNewRows: MutableList<MutableMap<String, String>> = mutableListOf()
    private val pendingDeletes: MutableSet<Int> = mutableSetOf()
    private val pendingUpdates: MutableMap<Int, MutableMap<String, String>> = mutableMapOf()

    fun loadTableData(
        driver: DatabaseDriver,
        tableName: String,
        page: Int = 0,
    ) {
        viewModelScope.launch {
            updateStateFlow(_uiState) { it.copy(isLoading = true, error = null, currentPage = page) }

            try {
                val pageSize = _uiState.value.pageSize
                val offset = page * pageSize
                val sql = "SELECT * FROM $tableName LIMIT $pageSize OFFSET $offset"
                val result = withContext(Dispatchers.IO) { driver.executeQuery(sql) }

                when (result) {
                    is QueryResult.Success -> {
                        val countSql = "SELECT COUNT(*) FROM $tableName"
                        val countResult = withContext(Dispatchers.IO) { driver.executeQuery(countSql) }
                        val totalRows = when (countResult) {
                            is QueryResult.Success -> countResult.rows.firstOrNull()?.values?.firstOrNull()?.toIntOrNull() ?: 0
                            is QueryResult.Error -> 0
                        }

                        updateStateFlow(_uiState) {
                            it.copy(
                                isLoading = false,
                                columns = result.columnNames,
                                rows = result.rows.map { row -> result.columnNames.map { col -> row[col] ?: "" } },
                                totalRows = totalRows,
                                error = null,
                            )
                        }
                    }
                    is QueryResult.Error -> {
                        updateStateFlow(_uiState) {
                            it.copy(
                                isLoading = false,
                                error = "Failed to load table data: ${result.message}",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                updateStateFlow(_uiState) {
                    it.copy(
                        isLoading = false,
                        error = "Error loading table data: ${e.message}",
                    )
                }
            }
        }
    }

    fun addNewRow() {
        val columns = _uiState.value.columns
        val emptyRow = columns.associateWith { "" }.toMutableMap()
        pendingNewRows.add(emptyRow)
        updatePendingChangesFlag()
    }

    fun markRowForDeletion(rowIndex: Int) {
        val currentRows = _uiState.value.rows
        if (rowIndex < pendingNewRows.size) {
            pendingNewRows.removeAt(rowIndex)
        } else {
            val adjustedIndex = rowIndex - pendingNewRows.size
            pendingDeletes.add(adjustedIndex)
        }
        updatePendingChangesFlag()
    }

    fun updateCell(
        rowIndex: Int,
        columnName: String,
        newValue: String,
    ) {
        if (rowIndex < pendingNewRows.size) {
            pendingNewRows[rowIndex][columnName] = newValue
        } else {
            val adjustedIndex = rowIndex - pendingNewRows.size
            pendingUpdates.getOrPut(adjustedIndex) { mutableMapOf() }[columnName] = newValue
        }
        updatePendingChangesFlag()
    }

    suspend fun commitChanges(
        driver: DatabaseDriver,
        tableName: String,
    ): Result<String> {
        return try {
            val statements = mutableListOf<String>()

            val columns = _uiState.value.columns
            pendingNewRows.forEach { row ->
                val colNames = columns.joinToString(", ")
                val values = columns.map { col -> "'${row[col]?.replace("'", "''")}'" }.joinToString(", ")
                statements.add("INSERT INTO $tableName ($colNames) VALUES ($values)")
            }

            val currentRows = _uiState.value.rows
            pendingUpdates.forEach { (rowIdx, changes) ->
                if (rowIdx < currentRows.size) {
                    val row = currentRows[rowIdx]
                    val idColumn = columns.firstOrNull() ?: return@forEach
                    val idValue = row.firstOrNull() ?: return@forEach

                    changes.forEach { (colName, newValue) ->
                        val rawSql = "UPDATE $tableName SET $colName = '${newValue.replace("'", "''")}' WHERE $idColumn = '$idValue'"
                        statements.add(rawSql)
                    }
                }
            }

            pendingDeletes.forEach { rowIdx ->
                if (rowIdx < currentRows.size) {
                    val row = currentRows[rowIdx]
                    val idColumn = columns.firstOrNull() ?: return@forEach
                    val idValue = row.firstOrNull() ?: return@forEach
                    statements.add("DELETE FROM $tableName WHERE $idColumn = '$idValue'")
                }
            }

            withContext(Dispatchers.IO) {
                statements.forEach { sql ->
                    val result = driver.executeQuery(sql)
                    if (result is QueryResult.Error) {
                        throw Exception("Failed to execute: $sql - ${result.message}")
                    }
                }
            }

            pendingNewRows.clear()
            pendingDeletes.clear()
            pendingUpdates.clear()
            updatePendingChangesFlag()

            Result.success("Committed ${statements.size} changes successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun discardChanges() {
        pendingNewRows.clear()
        pendingDeletes.clear()
        pendingUpdates.clear()
        updatePendingChangesFlag()
    }

    fun refresh(
        driver: DatabaseDriver,
        tableName: String,
    ) {
        discardChanges()
        loadTableData(driver, tableName, _uiState.value.currentPage)
    }

    fun nextPage(
        driver: DatabaseDriver,
        tableName: String,
    ) {
        val currentPage = _uiState.value.currentPage
        val pageSize = _uiState.value.pageSize
        val totalRows = _uiState.value.totalRows
        val maxPage = (totalRows + pageSize - 1) / pageSize - 1
        if (currentPage < maxPage) {
            loadTableData(driver, tableName, currentPage + 1)
        }
    }

    fun previousPage(
        driver: DatabaseDriver,
        tableName: String,
    ) {
        val currentPage = _uiState.value.currentPage
        if (currentPage > 0) {
            loadTableData(driver, tableName, currentPage - 1)
        }
    }

    private fun updatePendingChangesFlag() {
        val hasPending = pendingNewRows.isNotEmpty() || pendingDeletes.isNotEmpty() || pendingUpdates.isNotEmpty()
        updateStateFlow(_uiState) { it.copy(hasPendingChanges = hasPending) }
    }
}
