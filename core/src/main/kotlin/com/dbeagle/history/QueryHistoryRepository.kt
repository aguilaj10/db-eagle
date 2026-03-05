package com.dbeagle.history

import com.dbeagle.model.QueryHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and retrieving query history entries.
 */
interface QueryHistoryRepository {
    /**
     * Adds a new entry to the history.
     */
    fun add(entry: QueryHistoryEntry)

    /**
     * Returns all entries, most recent first.
     */
    fun getAll(): List<QueryHistoryEntry>

    /**
     * Returns all entries as a Flow, emitting updates when history changes.
     */
    fun getAllFlow(): Flow<List<QueryHistoryEntry>>

    /**
     * Clears all history entries.
     */
    fun clear()
}
