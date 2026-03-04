package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
data class ColumnMetadata(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val defaultValue: String? = null
)
