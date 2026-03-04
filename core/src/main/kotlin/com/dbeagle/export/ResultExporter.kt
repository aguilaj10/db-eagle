package com.dbeagle.export

import com.dbeagle.model.QueryResult
import com.dbeagle.query.PaginatedResultSet
import java.io.BufferedWriter
import java.io.File

/**
 * Exports query results to various formats with streaming support for large result sets.
 */
interface ResultExporter {
    /**
     * Export results to a file. This method streams data page-by-page to keep memory bounded.
     * @param outputFile The file to write to
     * @param firstPage The first page of results (already loaded)
     * @param resultSet Optional paginated result set for fetching additional pages
     * @param onProgress Optional callback invoked after each page with (currentRowCount, isDone)
     */
    suspend fun export(
        outputFile: File,
        firstPage: QueryResult.Success,
        resultSet: PaginatedResultSet?,
        onProgress: ((currentRowCount: Int, isDone: Boolean) -> Unit)? = null
    )
}

/**
 * CSV exporter with proper escaping for quotes, commas, and newlines.
 */
class CsvExporter : ResultExporter {
    override suspend fun export(
        outputFile: File,
        firstPage: QueryResult.Success,
        resultSet: PaginatedResultSet?,
        onProgress: ((currentRowCount: Int, isDone: Boolean) -> Unit)?
    ) {
        outputFile.bufferedWriter().use { writer ->
            // Write header
            writeHeaderRow(writer, firstPage.columnNames)
            
            var rowCount = 0
            
            // Write first page rows
            firstPage.rows.forEach { row ->
                writeDataRow(writer, firstPage.columnNames, row)
                rowCount++
            }
            onProgress?.invoke(rowCount, resultSet?.hasMore() != true)
            
            // Fetch and write additional pages if available
            var rs = resultSet
            while (rs?.hasMore() == true) {
                val page = rs.fetchNext() ?: break
                page.rows.forEach { row ->
                    writeDataRow(writer, firstPage.columnNames, row)
                    rowCount++
                }
                onProgress?.invoke(rowCount, !rs.hasMore())
            }
        }
    }
    
    private fun writeHeaderRow(writer: BufferedWriter, columns: List<String>) {
        writer.write(columns.joinToString(",") { escapeCsvField(it) })
        writer.newLine()
    }
    
    private fun writeDataRow(writer: BufferedWriter, columns: List<String>, row: Map<String, String>) {
        writer.write(columns.joinToString(",") { col -> escapeCsvField(row[col] ?: "") })
        writer.newLine()
    }
    
    private fun escapeCsvField(field: String): String {
        // If field contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            return "\"${field.replace("\"", "\"\"")}\""
        }
        return field
    }
}

/**
 * JSON exporter that writes an array of objects with column names as keys.
 */
class JsonExporter : ResultExporter {
    override suspend fun export(
        outputFile: File,
        firstPage: QueryResult.Success,
        resultSet: PaginatedResultSet?,
        onProgress: ((currentRowCount: Int, isDone: Boolean) -> Unit)?
    ) {
        outputFile.bufferedWriter().use { writer ->
            writer.write("[\n")
            
            var rowCount = 0
            var isFirstRow = true
            
            // Write first page rows
            firstPage.rows.forEach { row ->
                if (!isFirstRow) writer.write(",\n")
                writeJsonRow(writer, firstPage.columnNames, row)
                isFirstRow = false
                rowCount++
            }
            onProgress?.invoke(rowCount, resultSet?.hasMore() != true)
            
            // Fetch and write additional pages if available
            var rs = resultSet
            while (rs?.hasMore() == true) {
                val page = rs.fetchNext() ?: break
                page.rows.forEach { row ->
                    if (!isFirstRow) writer.write(",\n")
                    writeJsonRow(writer, firstPage.columnNames, row)
                    isFirstRow = false
                    rowCount++
                }
                onProgress?.invoke(rowCount, !rs.hasMore())
            }
            
            writer.write("\n]")
        }
    }
    
    private fun writeJsonRow(writer: BufferedWriter, columns: List<String>, row: Map<String, String>) {
        writer.write("  {")
        writer.write(columns.joinToString(", ") { col ->
            "\"${escapeJsonString(col)}\": \"${escapeJsonString(row[col] ?: "")}\""
        })
        writer.write("}")
    }
    
    private fun escapeJsonString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * SQL exporter that writes INSERT statements for the exported data.
 */
class SqlExporter(private val tableName: String = "exported_data") : ResultExporter {
    override suspend fun export(
        outputFile: File,
        firstPage: QueryResult.Success,
        resultSet: PaginatedResultSet?,
        onProgress: ((currentRowCount: Int, isDone: Boolean) -> Unit)?
    ) {
        outputFile.bufferedWriter().use { writer ->
            var rowCount = 0
            
            // Write first page rows
            firstPage.rows.forEach { row ->
                writeInsertStatement(writer, tableName, firstPage.columnNames, row)
                rowCount++
            }
            onProgress?.invoke(rowCount, resultSet?.hasMore() != true)
            
            // Fetch and write additional pages if available
            var rs = resultSet
            while (rs?.hasMore() == true) {
                val page = rs.fetchNext() ?: break
                page.rows.forEach { row ->
                    writeInsertStatement(writer, tableName, firstPage.columnNames, row)
                    rowCount++
                }
                onProgress?.invoke(rowCount, !rs.hasMore())
            }
        }
    }
    
    private fun writeInsertStatement(
        writer: BufferedWriter,
        table: String,
        columns: List<String>,
        row: Map<String, String>
    ) {
        val columnList = columns.joinToString(", ")
        val valuesList = columns.joinToString(", ") { col ->
            val value = row[col] ?: ""
            "'${escapeSqlString(value)}'"
        }
        writer.write("INSERT INTO $table ($columnList) VALUES ($valuesList);")
        writer.newLine()
    }
    
    private fun escapeSqlString(s: String): String {
        return s.replace("'", "''")
    }
}
