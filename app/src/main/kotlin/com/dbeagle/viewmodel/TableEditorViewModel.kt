package com.dbeagle.viewmodel

import com.dbeagle.ddl.ColumnDefinition
import com.dbeagle.ddl.ColumnType
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.ForeignKeyDefinition
import com.dbeagle.ddl.IndexDefinition
import com.dbeagle.ddl.TableDefinition
import com.dbeagle.ddl.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for TableEditorDialog that manages table editing state and business logic.
 *
 * Responsibilities:
 * - Manage table definition state (columns, constraints, indexes)
 * - Handle all user interactions through event handlers
 * - Validate table definitions before save
 * - Maintain separation between business logic and UI
 * - Provide unidirectional data flow through StateFlow
 *
 * Architecture:
 * - Immutable state (List instead of SnapshotStateList)
 * - Event-driven updates using bulk object update pattern
 * - Centralized validation logic
 * - Database-specific column type filtering
 *
 * Usage:
 * ```
 * val viewModel = TableEditorViewModel(
 *     existingTable = tableToEdit,
 *     existingIndexes = existingIndexes,
 *     allTables = listOf("users", "posts"),
 *     databaseType = "PostgreSQL"
 * )
 * val uiState by viewModel.uiState.collectAsState()
 *
 * // User adds and updates a column
 * viewModel.addColumn()
 * viewModel.updateColumn(0, ColumnDefinition(name = "id", type = ColumnType.INTEGER))
 *
 * // User saves
 * viewModel.validateAndSave { tableDef, indexes ->
 *     // Handle save
 * }
 * ```
 */
class TableEditorViewModel(
    existingTable: TableDefinition? = null,
    existingIndexes: List<IndexDefinition> = emptyList(),
    allTables: List<String> = emptyList(),
    databaseType: String? = null,
) : BaseViewModel() {

    /**
     * UI state for the Table Editor dialog.
     *
     * Contains all state needed to render the UI and perform validation.
     * All collections are immutable to enforce unidirectional data flow.
     *
     * @property tableName The name of the table being edited/created
     * @property columns List of column definitions
     * @property primaryKeyColumns List of column names that form the primary key
     * @property foreignKeys List of foreign key constraints
     * @property uniqueConstraints List of unique constraints (each is a list of column names)
     * @property indexes List of index definitions
     * @property isEditMode True if editing existing table, false if creating new
     * @property originalTableName The original table name (prevents renaming in edit mode)
     * @property allTables List of all available tables (for FK references)
     * @property databaseType The database type (PostgreSQL, SQLite, etc.)
     * @property selectedTab Currently selected tab (0=Columns, 1=Constraints, 2=Indexes)
     * @property validationError Validation error message, null if no errors
     * @property availableColumnTypes Column types available for current database
     */
    data class TableEditorUiState(
        val tableName: String = "",
        val columns: List<ColumnDefinition> = emptyList(),
        val primaryKeyColumns: List<String> = emptyList(),
        val foreignKeys: List<ForeignKeyDefinition> = emptyList(),
        val uniqueConstraints: List<List<String>> = emptyList(),
        val indexes: List<IndexDefinition> = emptyList(),
        val isEditMode: Boolean = false,
        val originalTableName: String? = null,
        val allTables: List<String> = emptyList(),
        val databaseType: String? = null,
        val selectedTab: Int = 0,
        val validationError: String? = null,
        val availableColumnTypes: List<ColumnType> = ColumnType.entries.toList(),
    )

    private val _uiState = MutableStateFlow(
        TableEditorUiState(
            tableName = existingTable?.name ?: "",
            columns = existingTable?.columns ?: emptyList(),
            primaryKeyColumns = existingTable?.primaryKey ?: emptyList(),
            foreignKeys = existingTable?.foreignKeys ?: emptyList(),
            uniqueConstraints = existingTable?.uniqueConstraints ?: emptyList(),
            indexes = existingIndexes,
            isEditMode = existingTable != null,
            originalTableName = existingTable?.name,
            allTables = allTables,
            databaseType = databaseType,
            availableColumnTypes = filterColumnTypesByDatabase(databaseType),
        ),
    )
    val uiState: StateFlow<TableEditorUiState> = _uiState.asStateFlow()

    // ========================================
    // TABLE-LEVEL EVENTS
    // ========================================

    /**
     * Updates the table name.
     * In edit mode, this is a no-op to prevent table renaming.
     *
     * @param newName The new table name
     */
    fun updateTableName(newName: String) {
        updateStateFlow(_uiState) { state ->
            if (state.isEditMode) {
                state // No-op in edit mode
            } else {
                state.copy(tableName = newName)
            }
        }
    }

    /**
     * Selects a tab in the editor.
     *
     * @param tabIndex Tab index (0=Columns, 1=Constraints, 2=Indexes)
     */
    fun selectTab(tabIndex: Int) {
        updateStateFlow(_uiState) { it.copy(selectedTab = tabIndex) }
    }

    /**
     * Validates the current table definition and invokes success callback if valid.
     * Updates validationError state if validation fails.
     *
     * @param onSuccess Callback invoked with TableDefinition and indexes if validation succeeds
     */
    fun validateAndSave(onSuccess: (TableDefinition, List<IndexDefinition>) -> Unit) {
        val tableDef = buildTableDefinition()

        when (val result = DDLValidator.validateTableDefinition(tableDef)) {
            is ValidationResult.Invalid -> {
                updateStateFlow(_uiState) {
                    it.copy(validationError = result.errors.joinToString("\n"))
                }
            }
            ValidationResult.Valid -> {
                updateStateFlow(_uiState) { it.copy(validationError = null) }
                onSuccess(tableDef, _uiState.value.indexes)
            }
        }
    }

    // ========================================
    // COLUMN MANAGEMENT EVENTS
    // ========================================

    /**
     * Adds a new column with default values.
     */
    fun addColumn() {
        updateStateFlow(_uiState) { state ->
            state.copy(
                columns = state.columns + ColumnDefinition(
                    name = "",
                    type = ColumnType.TEXT,
                    nullable = true,
                    defaultValue = null,
                ),
            )
        }
    }

    /**
     * Removes a column at the specified index.
     *
     * @param index Index of the column to remove
     */
    fun removeColumn(index: Int) {
        updateStateFlow(_uiState) { state ->
            state.copy(columns = state.columns.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * Updates a column at the specified index with a new definition.
     * This is a bulk update method for efficiency.
     *
     * @param index Index of the column to update
     * @param updatedColumn The new column definition
     */
    fun updateColumn(index: Int, updatedColumn: ColumnDefinition) {
        updateStateFlow(_uiState) { state ->
            val newColumns = state.columns.toMutableList().apply {
                set(index, updatedColumn)
            }
            state.copy(columns = newColumns)
        }
    }

    // ========================================
    // PRIMARY KEY EVENTS
    // ========================================

    /**
     * Toggles a column in the primary key.
     * If the column is in the primary key, it's removed; otherwise, it's added.
     *
     * @param columnName Name of the column to toggle
     */
    fun togglePrimaryKeyColumn(columnName: String) {
        updateStateFlow(_uiState) { state ->
            val newPkColumns = if (columnName in state.primaryKeyColumns) {
                state.primaryKeyColumns - columnName
            } else {
                state.primaryKeyColumns + columnName
            }
            state.copy(primaryKeyColumns = newPkColumns)
        }
    }

    // ========================================
    // FOREIGN KEY EVENTS
    // ========================================

    /**
     * Adds a new foreign key with default empty values.
     */
    fun addForeignKey() {
        updateStateFlow(_uiState) { state ->
            state.copy(
                foreignKeys = state.foreignKeys + ForeignKeyDefinition(
                    name = null,
                    columns = emptyList(),
                    refTable = "",
                    refColumns = emptyList(),
                    onDelete = null,
                    onUpdate = null,
                ),
            )
        }
    }

    /**
     * Removes a foreign key at the specified index.
     *
     * @param index Index of the foreign key to remove
     */
    fun removeForeignKey(index: Int) {
        updateStateFlow(_uiState) { state ->
            state.copy(foreignKeys = state.foreignKeys.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * Updates a foreign key at the specified index with a new definition.
     * This is a bulk update method for efficiency.
     *
     * @param index Index of the foreign key to update
     * @param updatedFk The new foreign key definition
     */
    fun updateForeignKey(index: Int, updatedFk: ForeignKeyDefinition) {
        updateStateFlow(_uiState) { state ->
            val newFks = state.foreignKeys.toMutableList().apply { set(index, updatedFk) }
            state.copy(foreignKeys = newFks)
        }
    }

    // ========================================
    // UNIQUE CONSTRAINT EVENTS
    // ========================================

    /**
     * Adds a new unique constraint with no columns selected.
     */
    fun addUniqueConstraint() {
        updateStateFlow(_uiState) { state ->
            state.copy(uniqueConstraints = state.uniqueConstraints + emptyList())
        }
    }

    /**
     * Removes a unique constraint at the specified index.
     *
     * @param index Index of the unique constraint to remove
     */
    fun removeUniqueConstraint(index: Int) {
        updateStateFlow(_uiState) { state ->
            state.copy(uniqueConstraints = state.uniqueConstraints.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * Updates the columns of a unique constraint at the specified index.
     *
     * @param index Index of the unique constraint
     * @param columns List of column names in the constraint
     */
    fun updateUniqueConstraintColumns(index: Int, columns: List<String>) {
        updateStateFlow(_uiState) { state ->
            val updated = state.uniqueConstraints.toMutableList().apply { set(index, columns) }
            state.copy(uniqueConstraints = updated)
        }
    }

    // ========================================
    // INDEX EVENTS
    // ========================================

    /**
     * Adds a new index with default empty values.
     */
    fun addIndex() {
        updateStateFlow(_uiState) { state ->
            state.copy(
                indexes = state.indexes + IndexDefinition(
                    name = "",
                    tableName = state.tableName,
                    columns = emptyList(),
                    unique = false,
                ),
            )
        }
    }

    /**
     * Removes an index at the specified index.
     *
     * @param index Index of the index definition to remove
     */
    fun removeIndex(index: Int) {
        updateStateFlow(_uiState) { state ->
            state.copy(indexes = state.indexes.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * Updates an index at the specified index with a new definition.
     * This is a bulk update method for efficiency.
     *
     * @param index Index of the index definition to update
     * @param updatedIndex The new index definition
     */
    fun updateIndex(index: Int, updatedIndex: IndexDefinition) {
        updateStateFlow(_uiState) { state ->
            val newIndexes = state.indexes.toMutableList().apply { set(index, updatedIndex) }
            state.copy(indexes = newIndexes)
        }
    }

    /**
     * Generates a conventional index name based on table name and selected columns.
     * Format: idx_tablename_col1_col2_...
     *
     * @param index Index of the index definition
     */
    fun generateIndexName(index: Int) {
        val state = _uiState.value
        val idx = state.indexes.getOrNull(index) ?: return

        if (idx.columns.isNotEmpty()) {
            val generatedName = "idx_${state.tableName}_${idx.columns.joinToString("_")}"
            updateIndex(index, idx.copy(name = generatedName))
        }
    }

    // ========================================
    // PRIVATE HELPER FUNCTIONS
    // ========================================

    /**
     * Builds a TableDefinition from current state for validation and saving.
     *
     * @return TableDefinition constructed from current UI state
     */
    private fun buildTableDefinition(): TableDefinition {
        val state = _uiState.value
        return TableDefinition(
            name = state.tableName,
            columns = state.columns,
            primaryKey = state.primaryKeyColumns.takeIf { it.isNotEmpty() },
            foreignKeys = state.foreignKeys,
            uniqueConstraints = state.uniqueConstraints,
        )
    }

    /**
     * Filters column types based on database type.
     * PostgreSQL supports all types; SQLite has limited type support.
     *
     * @param dbType The database type (e.g., "PostgreSQL", "SQLite")
     * @return List of supported ColumnType values for the database
     */
    private fun filterColumnTypesByDatabase(dbType: String?): List<ColumnType> = if (dbType == "PostgreSQL") {
        ColumnType.entries.toList()
    } else {
        // SQLite and others don't support PostgreSQL-specific types
        ColumnType.entries.filter {
            it !in listOf(
                ColumnType.SERIAL,
                ColumnType.SMALLSERIAL,
                ColumnType.BIGSERIAL,
                ColumnType.UUID,
                ColumnType.JSON,
                ColumnType.JSONB,
                ColumnType.SMALLINT,
                ColumnType.DOUBLE_PRECISION,
            )
        }
    }
}
