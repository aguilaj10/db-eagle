package com.dbeagle.ddl

import kotlinx.serialization.Serializable

/**
 * Represents a user-friendly error message for DDL operations.
 */
@Serializable
data class UserFriendlyError(
    val title: String,
    val description: String,
    val suggestion: String,
)

/**
 * Maps SQL error codes (SQLSTATE) to user-friendly error messages.
 *
 * This utility provides human-readable error messages for common DDL failures,
 * primarily targeting PostgreSQL SQLSTATE codes. For unknown errors or other
 * databases (SQLite), it falls back to displaying the original error message.
 */
object DDLErrorMapper {

    /**
     * Maps a SQL error to a user-friendly error message.
     *
     * @param sqlState The 5-character PostgreSQL SQLSTATE code (e.g., "42P07")
     * @param message The original error message from the database
     * @return A UserFriendlyError with title, description, and suggestion
     */
    fun mapError(sqlState: String?, message: String): UserFriendlyError = when (sqlState) {
        "42P07" -> UserFriendlyError(
            title = "Duplicate Table",
            description = "A table with this name already exists.",
            suggestion = "Choose a different table name or drop the existing table first.",
        )

        "42P01" -> UserFriendlyError(
            title = "Table Not Found",
            description = "The specified table does not exist.",
            suggestion = "Check the table name and try again.",
        )

        "23503" -> UserFriendlyError(
            title = "Foreign Key Error",
            description = "Cannot create foreign key: the referenced table or column doesn't exist.",
            suggestion = "Verify the referenced table and column exist.",
        )

        "42703" -> UserFriendlyError(
            title = "Column Not Found",
            description = "The specified column doesn't exist in the table.",
            suggestion = "Check the column name spelling.",
        )

        "23505" -> UserFriendlyError(
            title = "Duplicate Value",
            description = "A unique constraint would be violated.",
            suggestion = "Ensure the value is unique or modify the constraint.",
        )

        "42601" -> UserFriendlyError(
            title = "SQL Syntax Error",
            description = "The generated SQL has a syntax error.",
            suggestion = "Please report this issue.",
        )

        else -> UserFriendlyError(
            title = "Database Error",
            description = message,
            suggestion = "Check the database logs for details.",
        )
    }
}
