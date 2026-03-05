package com.dbeagle.ddl

import kotlinx.serialization.Serializable

/**
 * Result of DDL validation.
 */
@Serializable
sealed class ValidationResult {
    @Serializable
    data object Valid : ValidationResult()
    
    @Serializable
    data class Invalid(val errors: List<String>) : ValidationResult()
}

/**
 * Validates DDL identifiers and definitions before execution.
 *
 * Performs structural and syntax validation without executing queries.
 * Checks for SQL injection patterns, identifier validity, and structural correctness.
 */
object DDLValidator {
    
    private const val MAX_IDENTIFIER_LENGTH = 128
    
    // PostgreSQL identifier pattern: letters, digits, underscore, dollar sign
    private val identifierRegex = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")
    
    // SQL injection patterns to reject
    private val dangerousPatterns = listOf(
        ";",
        "--",
        "/*",
        "*/",
        "DROP",
        "DELETE",
        "INSERT",
        "UPDATE",
        "SELECT",
        "TRUNCATE",
        "EXEC",
        "EXECUTE"
    )
    
    /**
     * Validates a SQL identifier (table name, column name, etc).
     *
     * Checks:
     * - Not empty or blank
     * - Reasonable length (max 128 chars)
     * - No SQL injection patterns
     * - Only valid identifier characters (letters, digits, underscore, dollar sign)
     *
     * @param name The identifier to validate
     * @return ValidationResult.Valid or ValidationResult.Invalid with error messages
     */
    fun validateIdentifier(name: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Identifier cannot be empty or blank")
            return ValidationResult.Invalid(errors)
        }
        
        if (name.length > MAX_IDENTIFIER_LENGTH) {
            errors.add("Identifier exceeds maximum length of $MAX_IDENTIFIER_LENGTH characters: '$name'")
        }
        
        // Check for SQL injection patterns (case-insensitive)
        val upperName = name.uppercase()
        dangerousPatterns.forEach { pattern ->
            if (upperName.contains(pattern)) {
                errors.add("Identifier contains dangerous pattern '$pattern': '$name'")
            }
        }
        
        // Check identifier format
        if (!identifierRegex.matches(name)) {
            errors.add("Identifier contains invalid characters (only letters, digits, underscore, dollar sign allowed): '$name'")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validates a table definition.
     *
     * Checks:
     * - Table has at least one column
     * - No duplicate column names
     * - Valid table name
     * - All column names valid
     *
     * @param table The table definition to validate
     * @return ValidationResult.Valid or ValidationResult.Invalid with error messages
     */
    fun validateTableDefinition(table: TableDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate table name
        when (val nameResult = validateIdentifier(table.name)) {
            is ValidationResult.Invalid -> {
                errors.add("Invalid table name:")
                errors.addAll(nameResult.errors.map { "  - $it" })
            }
            ValidationResult.Valid -> {} // OK
        }
        
        // Check at least one column
        if (table.columns.isEmpty()) {
            errors.add("Table must have at least one column")
            return ValidationResult.Invalid(errors)
        }
        
        // Check for duplicate column names
        val columnNames = table.columns.map { it.name }
        val duplicates = columnNames.groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        
        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate column names found: ${duplicates.joinToString(", ")}")
        }
        
        // Validate each column
        table.columns.forEach { column ->
            when (val colResult = validateColumnDefinition(column)) {
                is ValidationResult.Invalid -> {
                    errors.add("Invalid column '${column.name}':")
                    errors.addAll(colResult.errors.map { "  - $it" })
                }
                ValidationResult.Valid -> {} // OK
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validates a column definition.
     *
     * Checks:
     * - Column name valid
     * - Type is valid (enforced by type system - ColumnType sealed class)
     *
     * @param column The column definition to validate
     * @return ValidationResult.Valid or ValidationResult.Invalid with error messages
     */
    fun validateColumnDefinition(column: ColumnDefinition): ValidationResult {
        // Validate column name
        when (val nameResult = validateIdentifier(column.name)) {
            is ValidationResult.Invalid -> {
                return ValidationResult.Invalid(
                    listOf("Invalid column name:") + nameResult.errors.map { "  - $it" }
                )
            }
            ValidationResult.Valid -> {} // OK
        }
        
        // Type is already enforced by ColumnType sealed class - no validation needed
        
        return ValidationResult.Valid
    }
}
