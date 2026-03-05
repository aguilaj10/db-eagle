package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
data class IndexMetadata(
    val name: String,
    val tableName: String,
    val columns: List<String>,
    val unique: Boolean,
    val type: String? = null,
)
