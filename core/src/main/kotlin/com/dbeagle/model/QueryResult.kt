package com.dbeagle.model

import com.dbeagle.query.PaginatedResultSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class QueryResult {
    @Serializable
    data class Success(
        val columnNames: List<String>,
        val rows: List<Map<String, String>>,
        @Transient
        val resultSet: PaginatedResultSet? = null,
    ) : QueryResult()

    @Serializable
    data class Error(
        val message: String,
        val errorCode: String? = null,
    ) : QueryResult()
}
