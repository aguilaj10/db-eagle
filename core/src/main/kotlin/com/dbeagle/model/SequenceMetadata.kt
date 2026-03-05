package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
data class SequenceMetadata(
    val name: String,
    val schema: String,
    val startValue: Long,
    val increment: Long,
    val minValue: Long,
    val maxValue: Long,
    val cycle: Boolean,
    val ownedByTable: String? = null,
    val ownedByColumn: String? = null,
)
