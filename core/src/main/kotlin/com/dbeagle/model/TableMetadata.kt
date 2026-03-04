package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
data class TableMetadata(
    val name: String,
    val schema: String,
    val columns: List<ColumnMetadata>,
    val primaryKey: List<String> = emptyList(),
    val indexes: List<String> = emptyList(),
)
