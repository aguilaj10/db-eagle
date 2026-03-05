package com.dbeagle.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FavoriteQuery(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val query: String,
    val tags: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
)
