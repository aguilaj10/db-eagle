package com.dbeagle.viewmodel

import com.dbeagle.ddl.ColumnDefinition
import com.dbeagle.ddl.ConstraintDefinition
import com.dbeagle.ddl.DDLDialect
import com.dbeagle.ddl.IndexDefinition
import com.dbeagle.ddl.PostgreSQLDDLDialect
import com.dbeagle.ddl.SQLiteDDLDialect
import com.dbeagle.ddl.SequenceChanges
import com.dbeagle.ddl.TableDefinition
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.IndexMetadata
import com.dbeagle.model.SequenceMetadata
import com.dbeagle.model.TableMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for SchemaBrowserScreen managing dialog state, DDL preview, and schema operations.
 *
 * This ViewModel follows the established pattern:
 * - Extends BaseViewModel for coroutine scope and StateFlow utilities
 * - Uses nested data classes for state organization
 * - Updates state via updateStateFlow() helper
 * - Wraps SchemaEditorViewModel static methods for DDL operations
 * - Stores non-serializable execution lambdas as private fields
 *
 * State Management:
 * - DialogState: Editor dialog visibility and editing context
 * - DDLPreviewState: DDL preview dialog state
 * - ErrorState: Error dialog state
 * - SchemaBrowserUiState: Combined UI state
 *
 * DDL Operations:
 * - Prepare DDL methods: Generate DDL strings for preview
 * - Execute methods: Run DDL operations and handle errors
 * - Helper methods: Build definitions, compute changes, execute SQL
 */
class SchemaBrowserViewModel : BaseViewModel() {

    /**
     * Dialog state for table/sequence/view/index editors.
     */
    data class DialogState(
        val showTableEditor: Boolean = false,
        val showSequenceEditor: Boolean = false,
        val showViewEditor: Boolean = false,
        val showIndexEditor: Boolean = false,
        val editingTable: String? = null,
        val editingSequence: String? = null,
    )

    /**
     * DDL preview dialog state.
     */
    data class DDLPreviewState(
        val isVisible: Boolean = false,
        val ddlSql: String = "",
        val isDestructive: Boolean = false,
    )

    /**
     * Error dialog state.
     */
    data class ErrorState(
        val isVisible: Boolean = false,
        val message: String = "",
    )

    /**
     * Combined UI state for SchemaBrowserScreen.
     */
    data class SchemaBrowserUiState(
        val dialog: DialogState = DialogState(),
        val ddlPreview: DDLPreviewState = DDLPreviewState(),
        val error: ErrorState = ErrorState(),
    )

    private val _uiState = MutableStateFlow(SchemaBrowserUiState())
    val uiState: StateFlow<SchemaBrowserUiState> = _uiState.asStateFlow()

    /**
     * Stores the pending DDL execution lambda (not serializable, kept outside state).
     */
    private var pendingDDLExecution: (suspend () -> Result<Unit>)? = null

    // ========== Dialog Actions ==========

    /**
     * Shows the table editor dialog, optionally for editing an existing table.
     *
     * @param tableName The name of the table to edit, or null to create a new table
     */
    fun showTableEditor(tableName: String? = null) {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(
                    showTableEditor = true,
                    editingTable = tableName,
                ),
            )
        }
    }

    /**
     * Hides the table editor dialog.
     */
    fun hideTableEditor() {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(
                    showTableEditor = false,
                    editingTable = null,
                ),
            )
        }
    }

    /**
     * Shows the sequence editor dialog, optionally for editing an existing sequence.
     *
     * @param sequenceName The name of the sequence to edit, or null to create a new sequence
     */
    fun showSequenceEditor(sequenceName: String? = null) {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(
                    showSequenceEditor = true,
                    editingSequence = sequenceName,
                ),
            )
        }
    }

    /**
     * Hides the sequence editor dialog.
     */
    fun hideSequenceEditor() {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(
                    showSequenceEditor = false,
                    editingSequence = null,
                ),
            )
        }
    }

    /**
     * Shows the view editor dialog.
     */
    fun showViewEditor() {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(showViewEditor = true),
            )
        }
    }

    /**
     * Hides the view editor dialog.
     */
    fun hideViewEditor() {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(showViewEditor = false),
            )
        }
    }

    /**
     * Shows the index editor dialog.
     */
    fun showIndexEditor() {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(showIndexEditor = true),
            )
        }
    }

    /**
     * Hides the index editor dialog.
     */
    fun hideIndexEditor() {
        updateStateFlow(_uiState) {
            it.copy(
                dialog = it.dialog.copy(showIndexEditor = false),
            )
        }
    }

    // ========== DDL Preview Actions ==========

    /**
     * Shows the DDL preview dialog with the generated DDL and execution lambda.
     *
     * @param ddl The DDL SQL to preview
     * @param isDestructive Whether the operation is destructive (drop/delete)
     * @param execution The suspend function to execute the DDL on confirmation
     */
    fun showDDLPreview(
        ddl: String,
        isDestructive: Boolean,
        execution: suspend () -> Result<Unit>,
    ) {
        pendingDDLExecution = execution
        updateStateFlow(_uiState) {
            it.copy(
                ddlPreview = DDLPreviewState(
                    isVisible = true,
                    ddlSql = ddl,
                    isDestructive = isDestructive,
                ),
            )
        }
    }

    /**
     * Hides the DDL preview dialog and clears pending execution.
     */
    fun hideDDLPreview() {
        pendingDDLExecution = null
        updateStateFlow(_uiState) {
            it.copy(
                ddlPreview = DDLPreviewState(),
            )
        }
    }

    /**
     * Executes the pending DDL operation stored via showDDLPreview().
     *
     * @param onStatusUpdate Callback for status messages during execution
     * @param onSuccess Callback invoked on successful execution
     */
    fun executePendingDDL(
        onStatusUpdate: (String) -> Unit,
        onSuccess: () -> Unit,
    ) {
        val execution = pendingDDLExecution ?: return

        viewModelScope.launch {
            onStatusUpdate("Executing DDL...")

            val result = execution()

            result.onSuccess {
                onStatusUpdate("DDL executed successfully")
                hideDDLPreview()
                onSuccess()
            }.onFailure { error ->
                onStatusUpdate("DDL execution failed")
                hideDDLPreview()
                showError(error.message ?: "Unknown error occurred")
            }
        }
    }

    // ========== Error Actions ==========

    /**
     * Shows the error dialog with the specified message.
     *
     * @param message The error message to display
     */
    fun showError(message: String) {
        updateStateFlow(_uiState) {
            it.copy(
                error = ErrorState(
                    isVisible = true,
                    message = message,
                ),
            )
        }
    }

    /**
     * Hides the error dialog.
     */
    fun hideError() {
        updateStateFlow(_uiState) {
            it.copy(
                error = ErrorState(),
            )
        }
    }

    // ========== DDL Preparation Methods (Wrapping SchemaEditorViewModel) ==========

    /**
     * Prepares CREATE TABLE DDL for preview.
     *
     * @param driver The database driver
     * @param definition The table definition
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun prepareCreateTable(
        driver: DatabaseDriver,
        definition: TableDefinition,
    ): Result<String> = SchemaEditorViewModel.createTableDDL(driver, definition)

    /**
     * Prepares ALTER TABLE DDL for preview.
     *
     * @param driver The database driver
     * @param tableName The table name to alter
     * @param changes The changes to apply
     * @return Success with DDL string, or Failure with validation/support errors
     */
    suspend fun prepareAlterTable(
        driver: DatabaseDriver,
        tableName: String,
        changes: TableChanges,
    ): Result<String> = SchemaEditorViewModel.alterTableDDL(driver, tableName, changes)

    /**
     * Prepares CREATE SEQUENCE DDL for preview.
     *
     * @param driver The database driver
     * @param definition The sequence metadata
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun prepareCreateSequence(
        driver: DatabaseDriver,
        definition: SequenceMetadata,
    ): Result<String> = SchemaEditorViewModel.createSequenceDDL(driver, definition)

    /**
     * Prepares ALTER SEQUENCE DDL for preview.
     *
     * @param driver The database driver
     * @param name The sequence name to alter
     * @param changes The changes to apply
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun prepareAlterSequence(
        driver: DatabaseDriver,
        name: String,
        changes: SequenceChanges,
    ): Result<String> = SchemaEditorViewModel.alterSequenceDDL(driver, name, changes)

    /**
     * Prepares DROP TABLE DDL for preview.
     *
     * @param driver The database driver
     * @param tableName The table name to drop
     * @param cascade Whether to cascade the drop to dependent objects
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun prepareDropTable(
        driver: DatabaseDriver,
        tableName: String,
        cascade: Boolean = true,
    ): Result<String> = SchemaEditorViewModel.dropTableDDL(driver, tableName, cascade)

    /**
     * Prepares DROP SEQUENCE DDL for preview.
     *
     * @param driver The database driver
     * @param name The sequence name to drop
     * @param ifExists Whether to use IF EXISTS clause
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun prepareDropSequence(
        driver: DatabaseDriver,
        name: String,
        ifExists: Boolean = true,
    ): Result<String> = SchemaEditorViewModel.dropSequenceDDL(driver, name, ifExists)

    // ========== Executor Factories ==========

    /**
     * Creates an executor lambda for CREATE TABLE operation.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Suspend lambda that executes the DDL and returns Result<Unit>
     */
    fun createTableExecutor(
        driver: DatabaseDriver,
        ddl: String,
    ): suspend () -> Result<Unit> = {
        SchemaEditorViewModel.executeTableCreate(driver, ddl)
    }

    /**
     * Creates an executor lambda for ALTER TABLE operation.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Suspend lambda that executes the DDL and returns Result<Unit>
     */
    fun alterTableExecutor(
        driver: DatabaseDriver,
        ddl: String,
    ): suspend () -> Result<Unit> = {
        SchemaEditorViewModel.executeTableAlter(driver, ddl)
    }

    /**
     * Creates an executor lambda for CREATE SEQUENCE operation.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Suspend lambda that executes the DDL and returns Result<Unit>
     */
    fun createSequenceExecutor(
        driver: DatabaseDriver,
        ddl: String,
    ): suspend () -> Result<Unit> = {
        SchemaEditorViewModel.executeSequenceCreate(driver, ddl)
    }

    /**
     * Creates an executor lambda for ALTER SEQUENCE operation.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Suspend lambda that executes the DDL and returns Result<Unit>
     */
    fun alterSequenceExecutor(
        driver: DatabaseDriver,
        ddl: String,
    ): suspend () -> Result<Unit> = {
        SchemaEditorViewModel.executeSequenceAlter(driver, ddl)
    }

    /**
     * Creates an executor lambda for DROP TABLE operation.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Suspend lambda that executes the DDL and returns Result<Unit>
     */
    fun dropTableExecutor(
        driver: DatabaseDriver,
        ddl: String,
    ): suspend () -> Result<Unit> = {
        SchemaEditorViewModel.executeTableDrop(driver, ddl)
    }

    /**
     * Creates an executor lambda for DROP SEQUENCE operation.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Suspend lambda that executes the DDL and returns Result<Unit>
     */
    fun dropSequenceExecutor(
        driver: DatabaseDriver,
        ddl: String,
    ): suspend () -> Result<Unit> = {
        SchemaEditorViewModel.executeSequenceDrop(driver, ddl)
    }

    // ========== Helper Methods ==========

    /**
     * Determines the DDL dialect based on the driver type.
     *
     * @param driver The database driver
     * @return The appropriate DDLDialect implementation
     */
    fun getDialectForDriver(driver: DatabaseDriver): DDLDialect = when (driver.getName()) {
        "PostgreSQL" -> PostgreSQLDDLDialect
        "SQLite" -> SQLiteDDLDialect
        else -> throw IllegalArgumentException("Unsupported driver type: ${driver.getName()}")
    }

    /**
     * Builds a TableDefinition from existing table schema data.
     *
     * Constructs a TableDefinition from TableMetadata, used when editing an existing table.
     *
     * @param tableMetadata The table metadata
     * @return TableDefinition
     */
    fun buildExistingTableDefinition(
        tableMetadata: TableMetadata,
    ): TableDefinition {
        val columns = tableMetadata.columns.map { col ->
            ColumnDefinition(
                name = col.name,
                type = inferColumnType(col.type),
                nullable = col.nullable,
                defaultValue = col.defaultValue,
            )
        }

        return TableDefinition(
            name = tableMetadata.name,
            columns = columns,
            primaryKey = tableMetadata.primaryKey.takeIf { it.isNotEmpty() },
            foreignKeys = emptyList(),
            uniqueConstraints = emptyList(),
        )
    }

    /**
     * Infers generic ColumnType from database-specific type string.
     *
     * Maps common SQL type names to the generic ColumnType enum.
     * Defaults to TEXT for unknown types.
     *
     * @param typeString The database-specific type name
     * @return The inferred ColumnType
     */
    private fun inferColumnType(typeString: String): com.dbeagle.ddl.ColumnType {
        val normalized = typeString.uppercase()
        return when {
            normalized.contains("INT") && normalized.contains("BIG") -> com.dbeagle.ddl.ColumnType.BIGINT
            normalized.contains("INT") -> com.dbeagle.ddl.ColumnType.INTEGER
            normalized.contains("DECIMAL") || normalized.contains("NUMERIC") -> com.dbeagle.ddl.ColumnType.DECIMAL
            normalized.contains("BOOL") -> com.dbeagle.ddl.ColumnType.BOOLEAN
            normalized.contains("DATE") && !normalized.contains("TIME") -> com.dbeagle.ddl.ColumnType.DATE
            normalized.contains("TIMESTAMP") -> com.dbeagle.ddl.ColumnType.TIMESTAMP
            normalized.contains("BLOB") || normalized.contains("BYTEA") -> com.dbeagle.ddl.ColumnType.BLOB
            else -> com.dbeagle.ddl.ColumnType.TEXT
        }
    }

    /**
     * Builds a list of IndexDefinitions from existing schema data.
     *
     * Constructs IndexDefinition objects from IndexMetadata.
     *
     * @param indexMetadataList The index metadata list
     * @return List of IndexDefinitions
     */
    fun buildExistingIndexes(
        indexMetadataList: List<IndexMetadata>,
    ): List<IndexDefinition> {
        return indexMetadataList.map { index ->
            IndexDefinition(
                name = index.name,
                tableName = index.tableName,
                columns = index.columns,
                unique = index.unique,
            )
        }
    }

    /**
     * Computes the differences between existing and new table definitions.
     *
     * Analyzes two TableDefinitions to determine what columns, constraints, and indexes
     * need to be added or dropped to transform the existing table into the new definition.
     *
     * @param existing The current table definition
     * @param new The desired table definition
     * @param existingIndexes The current indexes on the table
     * @param newIndexes The desired indexes on the table
     * @return TableChanges object describing the differences
     */
    fun computeTableChanges(
        existing: TableDefinition,
        new: TableDefinition,
        existingIndexes: List<IndexDefinition>,
        newIndexes: List<IndexDefinition>,
    ): TableChanges {
        val existingColNames = existing.columns.map { it.name }.toSet()
        val newColNames = new.columns.map { it.name }.toSet()

        val addedColumns = new.columns.filter { it.name !in existingColNames }
        val droppedColumns = existing.columns.filter { it.name !in newColNames }.map { it.name }

        // Compute constraint changes (simplified - only handles primary key for now)
        val addedConstraints = mutableListOf<ConstraintDefinition>()
        val droppedConstraints = mutableListOf<String>()

        if (existing.primaryKey != new.primaryKey) {
            existing.primaryKey?.let { droppedConstraints.add("${existing.name}_pkey") }
            new.primaryKey?.let { addedConstraints.add(ConstraintDefinition.PrimaryKey(it)) }
        }

        // Compute index changes
        val existingIndexNames = existingIndexes.map { it.name }.toSet()
        val newIndexNames = newIndexes.map { it.name }.toSet()

        val addedIndexes = newIndexes.filter { it.name !in existingIndexNames }
        val droppedIndexes = existingIndexes.filter { it.name !in newIndexNames }.map { it.name }

        return TableChanges(
            addedColumns = addedColumns,
            droppedColumns = droppedColumns,
            addedConstraints = addedConstraints,
            droppedConstraints = droppedConstraints,
            addedIndexes = addedIndexes,
            droppedIndexes = droppedIndexes,
        )
    }

    /**
     * Computes the differences between existing and new sequence metadata.
     *
     * Analyzes two SequenceMetadata objects to determine what sequence properties
     * need to be altered.
     *
     * @param existing The current sequence metadata
     * @param new The desired sequence metadata
     * @return SequenceChanges object describing the differences
     */
    fun computeSequenceChanges(
        existing: SequenceMetadata,
        new: SequenceMetadata,
    ): SequenceChanges {
        return SequenceChanges(
            increment = if (existing.increment != new.increment) new.increment else null,
            minValue = if (existing.minValue != new.minValue) new.minValue else null,
            maxValue = if (existing.maxValue != new.maxValue) new.maxValue else null,
            restart = null, // Restart must be explicitly requested, not inferred from diff
        )
    }

    /**
     * Executes arbitrary DDL statement directly.
     *
     * This is a convenience wrapper around SchemaEditorViewModel.executeDDL
     * for cases where direct DDL execution is needed without the prepare/preview flow.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    suspend fun executeDDL(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        // Access private executeDDL via reflection or expose it in SchemaEditorViewModel
        // For now, we'll use the public execute methods as wrappers
        return SchemaEditorViewModel.executeTableCreate(driver, ddl)
    }
}
