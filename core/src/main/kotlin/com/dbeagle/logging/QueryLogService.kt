package com.dbeagle.logging

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * QueryStatus represents the outcome of a query execution.
 */
@Serializable
enum class QueryStatus {
    SUCCESS,
    ERROR,
}

/**
 * QueryLogEntry captures details about a SQL query execution for debugging and analysis.
 *
 * @property timestamp Unix timestamp in milliseconds when the query was executed
 * @property sql The SQL query that was executed
 * @property durationMs Query execution duration in milliseconds
 * @property status Whether the query succeeded or failed
 * @property rowCount Number of rows affected/returned (null if not applicable)
 * @property errorMessage Error message if status is ERROR (null otherwise)
 */
@Serializable
data class QueryLogEntry(
    val timestamp: Long,
    val sql: String,
    val durationMs: Long,
    val status: QueryStatus,
    val rowCount: Int? = null,
    val errorMessage: String? = null,
)

/**
 * QueryLogService manages SQL execution logs in NDJSON format.
 *
 * Logs are stored in ~/.dbeagle/query.log with one JSON object per line.
 * This format enables efficient appending and streaming without parsing the entire file.
 *
 * Usage:
 * ```
 * QueryLogService.logQuery(
 *     QueryLogEntry(
 *         timestamp = System.currentTimeMillis(),
 *         sql = "SELECT * FROM users",
 *         durationMs = 42,
 *         status = QueryStatus.SUCCESS,
 *         rowCount = 10
 *     )
 * )
 * ```
 */
object QueryLogService {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Override for test environments. When set, this path is used instead of ~/.dbeagle/query.log.
     * INTERNAL USE ONLY - for test isolation purposes.
     */
    @Volatile
    internal var testLogFile: File? = null

    private val logFile: File
        get() = testLogFile ?: defaultLogFile

    private val defaultLogFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".dbeagle")
        dir.mkdirs()
        File(dir, "query.log")
    }

    /**
     * Log a query execution entry to the log file.
     * Appends a single JSON line to the file (NDJSON format).
     *
     * @param entry The query log entry to write
     */
    fun logQuery(entry: QueryLogEntry) {
        try {
            val jsonLine = json.encodeToString(entry) + "\n"
            logFile.appendText(jsonLine)
        } catch (e: Exception) {
            // Silently fail if logging fails (don't crash the app)
            e.printStackTrace()
        }
    }

    /**
     * Read all query log entries from the log file.
     * Parses each line as a separate JSON object (NDJSON format).
     *
     * @return List of query log entries, or empty list if file doesn't exist or parsing fails
     */
    fun getLogs(): List<QueryLogEntry> {
        try {
            if (!logFile.exists()) {
                return emptyList()
            }

            return logFile.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        json.decodeFromString<QueryLogEntry>(line)
                    } catch (e: Exception) {
                        // Skip malformed lines
                        e.printStackTrace()
                        null
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Clear all query log entries by deleting the log file.
     */
    fun clearLogs() {
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get the path to the query log file.
     *
     * @return Absolute path to query.log
     */
    fun getLogPath(): String = logFile.absolutePath
}
