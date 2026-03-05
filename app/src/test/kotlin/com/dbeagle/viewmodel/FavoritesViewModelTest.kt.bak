package com.dbeagle.viewmodel

import com.dbeagle.favorites.FavoritesRepository
import com.dbeagle.model.FavoriteQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoritesViewModelTest {

    /**
     * Fake FavoritesRepository for testing without file system dependencies.
     */
    private class FakeFavoritesRepository : FavoritesRepository {
        private val favorites = mutableListOf<FavoriteQuery>()

        override fun save(favorite: FavoriteQuery) {
            favorites.removeAll { it.id == favorite.id }
            favorites.add(0, favorite)
        }

        override fun getAll(): List<FavoriteQuery> = favorites.toList()

        override fun getById(id: String): FavoriteQuery? = favorites.find { it.id == id }

        override fun delete(id: String) {
            favorites.removeAll { it.id == id }
        }

        override fun search(query: String): List<FavoriteQuery> = if (query.isBlank()) {
            favorites
        } else {
            favorites.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.query.contains(query, ignoreCase = true)
            }
        }
    }

    private fun createTestRepository() = FakeFavoritesRepository()

    @Test
    fun initialState_loadsFromRepository() {
        val repository = createTestRepository()
        val favorite1 = FavoriteQuery(id = "1", name = "Query 1", query = "SELECT * FROM users")
        val favorite2 = FavoriteQuery(id = "2", name = "Query 2", query = "SELECT * FROM products")
        repository.save(favorite1)
        repository.save(favorite2)

        val viewModel = FavoritesViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(2, state.favorites.size, "Should load all favorites from repository")
        assertEquals("", state.searchQuery, "Search query should be empty initially")
        assertNull(state.editingFavorite, "editingFavorite should be null initially")
        assertNull(state.deletingFavorite, "deletingFavorite should be null initially")
    }

    @Test
    fun updateSearchQuery_updatesState() {
        val viewModel = FavoritesViewModel(createTestRepository())

        assertEquals("", viewModel.uiState.value.searchQuery)

        viewModel.updateSearchQuery("test query")

        assertEquals("test query", viewModel.uiState.value.searchQuery, "Search query should be updated")
    }

    @Test
    fun setEditingFavorite_setsAndClearsState() {
        val viewModel = FavoritesViewModel(createTestRepository())
        val favorite = FavoriteQuery(id = "1", name = "Test", query = "SELECT 1")

        assertNull(viewModel.uiState.value.editingFavorite)

        viewModel.setEditingFavorite(favorite)

        assertEquals(favorite, viewModel.uiState.value.editingFavorite, "Should set editing favorite")

        viewModel.setEditingFavorite(null)

        assertNull(viewModel.uiState.value.editingFavorite, "Should clear editing favorite")
    }

    @Test
    fun setDeletingFavorite_setsAndClearsState() {
        val viewModel = FavoritesViewModel(createTestRepository())
        val favorite = FavoriteQuery(id = "1", name = "Test", query = "SELECT 1")

        assertNull(viewModel.uiState.value.deletingFavorite)

        viewModel.setDeletingFavorite(favorite)

        assertEquals(favorite, viewModel.uiState.value.deletingFavorite, "Should set deleting favorite")

        viewModel.setDeletingFavorite(null)

        assertNull(viewModel.uiState.value.deletingFavorite, "Should clear deleting favorite")
    }

    @Test
    fun saveFavorite_savesToRepositoryAndRefreshes() {
        val repository = createTestRepository()
        val viewModel = FavoritesViewModel(repository)
        val favorite = FavoriteQuery(id = "1", name = "New Query", query = "SELECT * FROM test")

        viewModel.setEditingFavorite(favorite)
        assertEquals(favorite, viewModel.uiState.value.editingFavorite)

        viewModel.saveFavorite(favorite)

        assertEquals(1, viewModel.uiState.value.favorites.size, "Favorites should be refreshed after save")
        assertEquals(favorite.id, viewModel.uiState.value.favorites[0].id)
        assertNull(viewModel.uiState.value.editingFavorite, "editingFavorite should be cleared after save")
    }

    @Test
    fun saveFavorite_updatesExistingFavorite() {
        val repository = createTestRepository()
        val original = FavoriteQuery(id = "1", name = "Original", query = "SELECT 1")
        repository.save(original)

        val viewModel = FavoritesViewModel(repository)
        assertEquals(1, viewModel.uiState.value.favorites.size)
        assertEquals("Original", viewModel.uiState.value.favorites[0].name)

        val updated = original.copy(name = "Updated")
        viewModel.saveFavorite(updated)

        assertEquals(1, viewModel.uiState.value.favorites.size, "Should still have one favorite")
        assertEquals("Updated", viewModel.uiState.value.favorites[0].name, "Name should be updated")
    }

    @Test
    fun deleteFavorite_deletesFromRepositoryAndRefreshes() {
        val repository = createTestRepository()
        val favorite = FavoriteQuery(id = "1", name = "To Delete", query = "SELECT 1")
        repository.save(favorite)

        val viewModel = FavoritesViewModel(repository)
        assertEquals(1, viewModel.uiState.value.favorites.size)

        viewModel.setDeletingFavorite(favorite)
        assertEquals(favorite, viewModel.uiState.value.deletingFavorite)

        viewModel.deleteFavorite(favorite.id)

        assertEquals(0, viewModel.uiState.value.favorites.size, "Favorite should be deleted")
        assertNull(viewModel.uiState.value.deletingFavorite, "deletingFavorite should be cleared after delete")
    }

    @Test
    fun getDisplayedFavorites_returnsAllWhenNoSearch() {
        val repository = createTestRepository()
        val favorite1 = FavoriteQuery(id = "1", name = "Users Query", query = "SELECT * FROM users")
        val favorite2 = FavoriteQuery(id = "2", name = "Products Query", query = "SELECT * FROM products")
        repository.save(favorite1)
        repository.save(favorite2)

        val viewModel = FavoritesViewModel(repository)

        val displayed = viewModel.getDisplayedFavorites()

        assertEquals(2, displayed.size, "Should return all favorites when search query is blank")
    }

    @Test
    fun getDisplayedFavorites_returnsFilteredWhenSearching() {
        val repository = createTestRepository()
        val favorite1 = FavoriteQuery(id = "1", name = "Users Query", query = "SELECT * FROM users")
        val favorite2 = FavoriteQuery(id = "2", name = "Products Query", query = "SELECT * FROM products")
        repository.save(favorite1)
        repository.save(favorite2)

        val viewModel = FavoritesViewModel(repository)
        viewModel.updateSearchQuery("users")

        val displayed = viewModel.getDisplayedFavorites()

        assertEquals(1, displayed.size, "Should return filtered favorites when searching")
        assertEquals("1", displayed[0].id, "Should return the matching favorite")
    }

    @Test
    fun getDisplayedFavorites_searchIsCaseInsensitive() {
        val repository = createTestRepository()
        val favorite = FavoriteQuery(id = "1", name = "Users Query", query = "SELECT * FROM users")
        repository.save(favorite)

        val viewModel = FavoritesViewModel(repository)
        viewModel.updateSearchQuery("USERS")

        val displayed = viewModel.getDisplayedFavorites()

        assertEquals(1, displayed.size, "Search should be case-insensitive")
    }

    @Test
    fun getDisplayedFavorites_searchesNameAndQuery() {
        val repository = createTestRepository()
        val favorite1 = FavoriteQuery(id = "1", name = "Account Management", query = "SELECT * FROM products")
        val favorite2 = FavoriteQuery(id = "2", name = "Product List", query = "SELECT * FROM orders")
        repository.save(favorite1)
        repository.save(favorite2)

        val viewModel = FavoritesViewModel(repository)

        viewModel.updateSearchQuery("Account")
        val displayedByName = viewModel.getDisplayedFavorites()
        assertEquals(1, displayedByName.size, "Should find favorite by name")
        assertEquals("1", displayedByName[0].id)

        viewModel.updateSearchQuery("orders")
        val displayedByQuery = viewModel.getDisplayedFavorites()
        assertEquals(1, displayedByQuery.size, "Should find favorite by query text")
        assertEquals("2", displayedByQuery[0].id)
    }

    @Test
    fun refreshFavorites_reloadsFromRepository() {
        val repository = createTestRepository()
        val viewModel = FavoritesViewModel(repository)

        assertEquals(0, viewModel.uiState.value.favorites.size)

        // Add favorite directly to repository (simulating external change)
        val newFavorite = FavoriteQuery(id = "1", name = "New", query = "SELECT 1")
        repository.save(newFavorite)

        viewModel.refreshFavorites()

        assertEquals(1, viewModel.uiState.value.favorites.size, "Should reload favorites from repository")
        assertEquals(newFavorite.id, viewModel.uiState.value.favorites[0].id)
    }
}
