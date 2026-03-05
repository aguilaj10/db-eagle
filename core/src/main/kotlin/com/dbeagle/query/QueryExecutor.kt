package com.dbeagle.query

import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.logging.QueryLogEntry
import com.dbeagle.logging.QueryLogService
import com.dbeagle.logging.QueryStatus
import com.dbeagle.model.QueryResult
import com.dbeagle.settings.AppPreferences

class QueryExecutor(
    private val driver: DatabaseDriver,
    private val defaultPageSize: Int = getDefaultPageSize(),
) {
    init {
        require(defaultPageSize > 0) { "defaultPageSize must be > 0" }
    }

    suspend fun execute(
        sql: String,
        params: List<Any> = emptyList(),
        pageSize: Int = defaultPageSize,
    ): QueryResult {
        val trimmed = sql.trim()
        if (!looksLikeSelect(trimmed)) {
            val startTime = System.currentTimeMillis()
            val result = driver.executeQuery(sql, params)
            val duration = System.currentTimeMillis() - startTime

            try {
                QueryLogService.logQuery(
                    QueryLogEntry(
                        timestamp = startTime,
                        sql = sql,
                        durationMs = duration,
                        status = if (result is QueryResult.Success) QueryStatus.SUCCESS else QueryStatus.ERROR,
                        rowCount = (result as? QueryResult.Success)?.rows?.size,
                        errorMessage = (result as? QueryResult.Error)?.message,
                    ),
                )
            } catch (_: Exception) { /* silent */ }

            return result
        }

        require(pageSize > 0) { "pageSize must be > 0" }

        val baseSql = stripTrailingSemicolon(trimmed)
        val startTime = System.currentTimeMillis()
        val result = when (val first = executePage(baseSql = baseSql, params = params, pageSize = pageSize, offset = 0)) {
            is PageOutcome.Err -> first.result
            is PageOutcome.Ok -> {
                val rs =
                    ResultSetImpl(
                        baseSql = baseSql,
                        baseParams = params,
                        pageSize = pageSize,
                        nextOffset = pageSize,
                        hasMore = first.hasMore,
                    )
                first.page.copy(resultSet = rs)
            }
        }
        val duration = System.currentTimeMillis() - startTime

        try {
            QueryLogService.logQuery(
                QueryLogEntry(
                    timestamp = startTime,
                    sql = sql,
                    durationMs = duration,
                    status = if (result is QueryResult.Success) QueryStatus.SUCCESS else QueryStatus.ERROR,
                    rowCount = (result as? QueryResult.Success)?.rows?.size,
                    errorMessage = (result as? QueryResult.Error)?.message,
                ),
            )
        } catch (_: Exception) { /* silent */ }

        return result
    }

    private sealed interface PageOutcome {
        data class Ok(
            val page: QueryResult.Success,
            val hasMore: Boolean,
        ) : PageOutcome

        data class Err(
            val result: QueryResult.Error,
        ) : PageOutcome
    }

    private suspend fun executePage(
        baseSql: String,
        params: List<Any>,
        pageSize: Int,
        offset: Int,
    ): PageOutcome {
        val limit = pageSize + 1
        val paginatedSql = "SELECT * FROM ( $baseSql ) AS q LIMIT ? OFFSET ?"
        val paginatedParams = params + listOf(limit, offset)
        return when (val result = driver.executeQuery(paginatedSql, paginatedParams)) {
            is QueryResult.Error -> PageOutcome.Err(result)
            is QueryResult.Success -> {
                val hasMore = result.rows.size > pageSize
                val trimmedRows = if (hasMore) result.rows.take(pageSize) else result.rows
                PageOutcome.Ok(page = result.copy(rows = trimmedRows), hasMore = hasMore)
            }
        }
    }

    private inner class ResultSetImpl(
        private val baseSql: String,
        private val baseParams: List<Any>,
        private val pageSize: Int,
        private var nextOffset: Int,
        private var hasMore: Boolean,
    ) : PaginatedResultSet {
        override fun hasMore(): Boolean = hasMore

        override suspend fun fetchNext(): QueryResult.Success? {
            if (!hasMore) return null
            val outcome = executePage(baseSql = baseSql, params = baseParams, pageSize = pageSize, offset = nextOffset)
            if (outcome is PageOutcome.Err) {
                hasMore = false
                return null
            }

            val ok = outcome as PageOutcome.Ok
            nextOffset += pageSize
            hasMore = ok.hasMore

            return ok.page.copy(resultSet = this)
        }
    }

    private fun stripTrailingSemicolon(sql: String): String {
        val t = sql.trimEnd()
        return if (t.endsWith(";")) t.dropLast(1).trimEnd() else t
    }

    private fun looksLikeSelect(sql: String): Boolean {
        val s = sql.trimStart()
        if (s.isEmpty()) return false
        if (s.regionMatches(0, "select", 0, 6, ignoreCase = true)) return true
        if (s.regionMatches(0, "with", 0, 4, ignoreCase = true)) return true
        return false
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 1000

        private fun getDefaultPageSize(): Int = try {
            AppPreferences.load().resultLimit
        } catch (_: Exception) {
            DEFAULT_PAGE_SIZE
        }
    }
}
