package com.dbeagle.viewmodel

import com.dbeagle.favorites.FavoritesRepository
import com.dbeagle.model.FavoriteQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesViewModel(
    private val repository: FavoritesRepository,
) : BaseViewModel() {

    data class FavoritesUiState(
        val favorites: List<FavoriteQuery> = emptyList(),
        val searchQuery: String = "",
        val editingFavorite: FavoriteQuery? = null,
        val deletingFavorite: FavoriteQuery? = null,
    )

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        refreshFavorites()
    }

    fun refreshFavorites() {
        updateStateFlow(_uiState) { it.copy(favorites = repository.getAll()) }
    }

    fun getDisplayedFavorites(): List<FavoriteQuery> {
        val state = _uiState.value
        return if (state.searchQuery.isBlank()) state.favorites else repository.search(state.searchQuery)
    }

    fun updateSearchQuery(query: String) {
        updateStateFlow(_uiState) { it.copy(searchQuery = query) }
    }

    fun setEditingFavorite(favorite: FavoriteQuery?) {
        updateStateFlow(_uiState) { it.copy(editingFavorite = favorite) }
    }

    fun setDeletingFavorite(favorite: FavoriteQuery?) {
        updateStateFlow(_uiState) { it.copy(deletingFavorite = favorite) }
    }

    fun saveFavorite(updated: FavoriteQuery) {
        repository.save(updated)
        refreshFavorites()
        setEditingFavorite(null)
    }

    fun deleteFavorite(id: String) {
        repository.delete(id)
        refreshFavorites()
        setDeletingFavorite(null)
    }
}
