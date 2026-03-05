package com.dbeagle.favorites

import com.dbeagle.model.FavoriteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileFavoritesRepository(
    private val favoritesFile: File = File(System.getProperty("user.home"), ".dbeagle/favorites.json"),
) : FavoritesRepository {
    private val json = Json { prettyPrint = true }
    private val favoritesStateFlow = MutableStateFlow<List<FavoriteQuery>>(emptyList())

    init {
        favoritesFile.parentFile?.mkdirs()
        if (!favoritesFile.exists()) {
            favoritesFile.writeText("[]")
        }
        favoritesStateFlow.value = getAll()
    }

    override fun save(favorite: FavoriteQuery) {
        val current = getAll().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == favorite.id }

        if (existingIndex >= 0) {
            current[existingIndex] = favorite.copy(lastModified = System.currentTimeMillis())
        } else {
            current.add(favorite)
        }

        persist(current)
    }

    override fun getAll(): List<FavoriteQuery> {
        if (!favoritesFile.exists()) return emptyList()
        val text = favoritesFile.readText().trim()
        if (text.isEmpty() || text == "[]") return emptyList()
        return json
            .decodeFromString<List<FavoriteQuery>>(text)
            .sortedByDescending { it.lastModified }
    }

    override fun getAllFlow(): Flow<List<FavoriteQuery>> = favoritesStateFlow.asStateFlow()
        .map { it }
        .flowOn(Dispatchers.IO)

    override fun getById(id: String): FavoriteQuery? = getAll().firstOrNull { it.id == id }

    override fun delete(id: String) {
        val current = getAll().filter { it.id != id }
        persist(current)
    }

    override fun search(query: String): List<FavoriteQuery> {
        if (query.isBlank()) return getAll()

        val lowerQuery = query.lowercase()
        return getAll().filter { favorite ->
            favorite.name.lowercase().contains(lowerQuery) ||
                favorite.query.lowercase().contains(lowerQuery) ||
                favorite.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }

    private fun persist(favorites: List<FavoriteQuery>) {
        val tempFile = File.createTempFile("favorites", ".json", favoritesFile.parentFile)
        try {
            tempFile.writeText(json.encodeToString(favorites))
            try {
                Files.move(tempFile.toPath(), favoritesFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: UnsupportedOperationException) {
                Files.move(tempFile.toPath(), favoritesFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), favoritesFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            favoritesStateFlow.value = favorites.sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
