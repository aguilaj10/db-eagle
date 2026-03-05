package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
data class ForeignKeyRelationship(
    val fromTable: String,
    val fromColumn: String,
    val toTable: String,
    val toColumn: String,
)

@Serializable
data class SchemaMetadata(
    val tables: List<TableMetadata>,
    val views: List<String> = emptyList(),
    val indexes: List<String> = emptyList(),
    val indexDetails: List<IndexMetadata> = emptyList(),
    val foreignKeys: List<ForeignKeyRelationship> = emptyList(),
    val sequences: List<SequenceMetadata> = emptyList(),
)
