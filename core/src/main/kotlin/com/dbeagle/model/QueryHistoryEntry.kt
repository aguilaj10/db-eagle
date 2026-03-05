package com.dbeagle.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class QueryHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val connectionProfileId: String,
)
