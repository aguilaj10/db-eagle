package com.dbeagle.favorites

import com.dbeagle.model.FavoriteQuery
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and retrieving favorite queries.
 */
interface FavoritesRepository {
    /**
     * Saves a new favorite or updates an existing one by ID.
     */
    fun save(favorite: FavoriteQuery)

    /**
     * Returns all favorites, most recently modified first.
     */
    fun getAll(): List<FavoriteQuery>

    /**
     * Returns all favorites as a Flow, emitting updates when favorites change.
     */
    fun getAllFlow(): Flow<List<FavoriteQuery>>

    /**
     * Returns a single favorite by ID, or null if not found.
     */
    fun getById(id: String): FavoriteQuery?

    /**
     * Deletes a favorite by ID.
     */
    fun delete(id: String)

    /**
     * Searches favorites by name, query text, or tags.
     * Returns all favorites if query is blank.
     */
    fun search(query: String): List<FavoriteQuery>
}
