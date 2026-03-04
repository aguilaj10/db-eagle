package com.dbeagle.query

import com.dbeagle.model.QueryResult

interface PaginatedResultSet {
    fun hasMore(): Boolean

    suspend fun fetchNext(): QueryResult.Success?
}
