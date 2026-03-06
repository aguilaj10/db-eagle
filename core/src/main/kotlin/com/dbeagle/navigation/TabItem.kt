package com.dbeagle.navigation

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a tab in the dynamic tab system.
 *
 * @property id Unique identifier for the tab
 * @property type The type of content this tab displays
 * @property title Display name for the tab
 * @property connectionId Optional connection ID for context-aware tabs
 * @property tableName Optional table name for table-specific tabs (e.g., TableEditor)
 */
@Serializable
data class TabItem(
    val id: String = UUID.randomUUID().toString(),
    val type: TabType,
    val title: String,
    val connectionId: String? = null,
    val tableName: String? = null,
)

/**
 * Types of tabs available in the application.
 *
 * Note: SchemaBrowser has been removed as it moves inline under connections.
 */
@Serializable
enum class TabType {
    /** SQL query editor tab */
    QueryEditor,

    /** Favorites management tab */
    Favorites,

    /** Query history tab */
    History,

    /** Query execution log tab */
    QueryLog,

    /** Table data editor tab */
    TableEditor,
}
