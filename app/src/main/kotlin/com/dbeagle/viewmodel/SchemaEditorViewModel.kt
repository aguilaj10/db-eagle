package com.dbeagle.viewmodel

import com.dbeagle.ddl.ColumnDefinition
import com.dbeagle.ddl.ConstraintDefinition
import com.dbeagle.ddl.DDLDialect
import com.dbeagle.ddl.DDLErrorMapper
import com.dbeagle.ddl.DDLValidator
import com.dbeagle.ddl.IndexDDLBuilder
import com.dbeagle.ddl.IndexDefinition
import com.dbeagle.ddl.PostgreSQLDDLDialect
import com.dbeagle.ddl.SequenceChanges
import com.dbeagle.ddl.SequenceDDLBuilder
import com.dbeagle.ddl.SQLiteDDLDialect
import com.dbeagle.ddl.TableDDLBuilder
import com.dbeagle.ddl.TableDefinition
import com.dbeagle.ddl.UserFriendlyError
import com.dbeagle.ddl.ValidationResult
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.SequenceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

data class TableChanges(
    val addedColumns: List<ColumnDefinition> = emptyList(),
    val droppedColumns: List<String> = emptyList(),
    val addedConstraints: List<ConstraintDefinition> = emptyList(),
    val droppedConstraints: List<String> = emptyList(),
    val addedIndexes: List<IndexDefinition> = emptyList(),
    val droppedIndexes: List<String> = emptyList(),
)

/**
 * ViewModel for schema DDL operations (create/alter/drop sequences and tables).
 *
 * Handles:
 * - Input validation via DDLValidator
 * - DDL generation via SequenceDDLBuilder / TableDDLBuilder
 * - DDL execution via DatabaseDriver
 * - Error mapping via DDLErrorMapper
 *
 * Flow:
 * 1. Validate input
 * 2. Generate DDL string
 * 3. Return DDL for preview
 * 4. Execute DDL on user confirmation
 * 5. Map errors to user-friendly messages
 */
object SchemaEditorViewModel {
    
    /**
     * Generates CREATE SEQUENCE DDL for preview.
     *
     * Validates the sequence name and generates dialect-specific DDL.
     *
     * @param driver The database driver (used to determine dialect)
     * @param definition The sequence metadata (name, start, increment, etc.)
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun createSequenceDDL(
        driver: DatabaseDriver,
        definition: SequenceMetadata,
    ): Result<String> {
        return withContext(Dispatchers.Default) {
            // Validate identifier
            val nameValidation = DDLValidator.validateIdentifier(definition.name)
            if (nameValidation is ValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalArgumentException(nameValidation.errors.joinToString("; "))
                )
            }
            
            // Get dialect based on driver
            val dialect = getDialectForDriver(driver)
            
            // Generate DDL
            val ddl = SequenceDDLBuilder.buildCreateSequence(dialect, definition)
            
            Result.success(ddl)
        }
    }
    
    /**
     * Executes CREATE SEQUENCE DDL.
     *
     * Runs the DDL statement and maps any errors to user-friendly messages.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    suspend fun executeSequenceCreate(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        return executeDDL(driver, ddl)
    }
    
    /**
     * Generates ALTER SEQUENCE DDL for preview.
     *
     * Validates the sequence name and generates dialect-specific DDL.
     *
     * @param driver The database driver
     * @param name The sequence name to alter
     * @param changes The changes to apply (increment, min/max, restart)
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun alterSequenceDDL(
        driver: DatabaseDriver,
        name: String,
        changes: SequenceChanges,
    ): Result<String> {
        return withContext(Dispatchers.Default) {
            // Validate identifier
            val nameValidation = DDLValidator.validateIdentifier(name)
            if (nameValidation is ValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalArgumentException(nameValidation.errors.joinToString("; "))
                )
            }
            
            // Get dialect
            val dialect = getDialectForDriver(driver)
            
            // Generate DDL
            val ddl = SequenceDDLBuilder.buildAlterSequence(dialect, name, changes)
            
            Result.success(ddl)
        }
    }
    
    /**
     * Executes ALTER SEQUENCE DDL.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    suspend fun executeSequenceAlter(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        return executeDDL(driver, ddl)
    }
    
    /**
     * Generates DROP SEQUENCE DDL for preview.
     *
     * Validates the sequence name and generates dialect-specific DDL.
     *
     * @param driver The database driver
     * @param name The sequence name to drop
     * @param ifExists Whether to use IF EXISTS clause (default true)
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun dropSequenceDDL(
        driver: DatabaseDriver,
        name: String,
        ifExists: Boolean = true,
    ): Result<String> {
        return withContext(Dispatchers.Default) {
            // Validate identifier
            val nameValidation = DDLValidator.validateIdentifier(name)
            if (nameValidation is ValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalArgumentException(nameValidation.errors.joinToString("; "))
                )
            }
            
            // Get dialect
            val dialect = getDialectForDriver(driver)
            
            // Generate DDL
            val ddl = SequenceDDLBuilder.buildDropSequence(dialect, name, ifExists)
            
            Result.success(ddl)
        }
    }
    
    /**
     * Executes DROP SEQUENCE DDL.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    suspend fun executeSequenceDrop(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        return executeDDL(driver, ddl)
    }
    
    /**
     * Generates CREATE TABLE DDL for preview.
     *
     * Validates the table name and column definitions, then generates dialect-specific DDL.
     *
     * @param driver The database driver (used to determine dialect)
     * @param definition The table definition (name, columns, constraints)
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun createTableDDL(
        driver: DatabaseDriver,
        definition: TableDefinition,
    ): Result<String> {
        return withContext(Dispatchers.Default) {
            // Validate table definition
            val validation = DDLValidator.validateTableDefinition(definition)
            if (validation is ValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalArgumentException(validation.errors.joinToString("; "))
                )
            }
            
            // Get dialect based on driver
            val dialect = getDialectForDriver(driver)
            
            // Generate DDL
            val ddl = TableDDLBuilder.buildCreateTable(dialect, definition)
            
            Result.success(ddl)
        }
    }
    
    /**
     * Executes CREATE TABLE DDL.
     *
     * Runs the DDL statement and maps any errors to user-friendly messages.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    suspend fun executeTableCreate(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        return executeDDL(driver, ddl)
    }
    
    /**
     * Generates ALTER TABLE DDL for preview.
     *
     * Validates the table name and generates dialect-specific DDL for incremental changes.
     * Checks dialect support for DROP COLUMN and other ALTER operations.
     *
     * @param driver The database driver
     * @param tableName The table name to alter
     * @param changes The changes to apply (add/drop columns, constraints, indexes)
     * @return Success with DDL string, or Failure with validation/support errors
     */
    suspend fun alterTableDDL(
        driver: DatabaseDriver,
        tableName: String,
        changes: TableChanges,
    ): Result<String> {
        return withContext(Dispatchers.Default) {
            // Validate identifier
            val nameValidation = DDLValidator.validateIdentifier(tableName)
            if (nameValidation is ValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalArgumentException(nameValidation.errors.joinToString("; "))
                )
            }
            
            // Get dialect
            val dialect = getDialectForDriver(driver)
            
            // Check SQLite limitations
            if (changes.droppedColumns.isNotEmpty() && !dialect.supportsDropColumn()) {
                return@withContext Result.failure(
                    UnsupportedOperationException(
                        "ALTER TABLE DROP COLUMN is not supported by SQLite. " +
                        "To remove columns, you must recreate the table with the desired schema."
                    )
                )
            }
            
            // Generate DDL statements
            val statements = buildList<String> {
                // Add columns
                changes.addedColumns.forEach { col ->
                    add(TableDDLBuilder.buildAlterTableAddColumn(dialect, tableName, col))
                }
                
                // Drop columns
                changes.droppedColumns.forEach { col ->
                    add(TableDDLBuilder.buildAlterTableDropColumn(dialect, tableName, col))
                }
                
                // Add constraints
                changes.addedConstraints.forEach { constraint ->
                    add(TableDDLBuilder.buildAlterTableAddConstraint(dialect, tableName, constraint))
                }
                
                // Drop constraints
                changes.droppedConstraints.forEach { constraintName ->
                    add(TableDDLBuilder.buildAlterTableDropConstraint(dialect, tableName, constraintName))
                }
            }
            
            if (statements.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("No changes specified for ALTER TABLE operation")
                )
            }
            
            // Join multiple statements with semicolons
            val ddl = statements.joinToString(";\n") + ";"
            
            Result.success(ddl)
        }
    }
    
    /**
     * Executes ALTER TABLE DDL.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    suspend fun executeTableAlter(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        return executeDDL(driver, ddl)
    }
    
    /**
     * Generates DROP TABLE DDL for preview.
     *
     * Validates the table name and generates dialect-specific DDL.
     *
     * @param driver The database driver
     * @param tableName The table name to drop
     * @param cascade Whether to cascade the drop to dependent objects (default true)
     * @return Success with DDL string, or Failure with validation errors
     */
    suspend fun dropTableDDL(
        driver: DatabaseDriver,
        tableName: String,
        cascade: Boolean = true,
    ): Result<String> {
        return withContext(Dispatchers.Default) {
            // Validate identifier
            val nameValidation = DDLValidator.validateIdentifier(tableName)
            if (nameValidation is ValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalArgumentException(nameValidation.errors.joinToString("; "))
                )
            }
            
            // Get dialect
            val dialect = getDialectForDriver(driver)
            
            // Generate DDL
            val ddl = TableDDLBuilder.buildDropTable(dialect, tableName, cascade)
            
            Result.success(ddl)
        }
    }
    
    /**
     * Executes DROP TABLE DDL.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    suspend fun executeTableDrop(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        return executeDDL(driver, ddl)
    }
    
    /**
     * Executes arbitrary DDL statement.
     *
     * Maps SQL errors to user-friendly messages via DDLErrorMapper.
     *
     * @param driver The database driver
     * @param ddl The DDL string to execute
     * @return Success (Unit), or Failure with UserFriendlyError
     */
    private suspend fun executeDDL(
        driver: DatabaseDriver,
        ddl: String,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Execute DDL via driver
                val result = driver.executeQuery(ddl, emptyList())
                
                when (result) {
                    is com.dbeagle.model.QueryResult.Success -> {
                        Result.success(Unit)
                    }
                    is com.dbeagle.model.QueryResult.Error -> {
                        // Map error to user-friendly message
                        val userError = DDLErrorMapper.mapError(null, result.message)
                        Result.failure(DDLExecutionException(userError))
                    }
                }
            } catch (e: SQLException) {
                // Extract SQLSTATE and map to user-friendly error
                val sqlState = e.sqlState
                val userError = DDLErrorMapper.mapError(sqlState, e.message ?: "Unknown SQL error")
                Result.failure(DDLExecutionException(userError))
            } catch (e: Exception) {
                // Generic error
                val userError = DDLErrorMapper.mapError(null, e.message ?: "Unknown error")
                Result.failure(DDLExecutionException(userError))
            }
        }
    }
    
    /**
     * Determines the DDL dialect based on the driver type.
     *
     * @param driver The database driver
     * @return The appropriate DDLDialect implementation
     */
    private fun getDialectForDriver(driver: DatabaseDriver): DDLDialect {
        return when (driver.getName()) {
            "PostgreSQL" -> PostgreSQLDDLDialect
            "SQLite" -> SQLiteDDLDialect
            else -> throw IllegalArgumentException("Unsupported driver type: ${driver.getName()}")
        }
    }
}

/**
 * Exception wrapper for DDL execution errors with user-friendly messages.
 */
class DDLExecutionException(val userError: UserFriendlyError) : Exception(userError.description)
