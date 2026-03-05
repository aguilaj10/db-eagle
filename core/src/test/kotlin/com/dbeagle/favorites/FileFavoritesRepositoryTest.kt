package com.dbeagle.favorites

import com.dbeagle.model.FavoriteQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileFavoritesRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `save and getAll returns favorites sorted by most recently modified`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        val fav1 = FavoriteQuery(
            id = "fav1",
            name = "Users Query",
            query = "SELECT * FROM users",
            tags = listOf("sql", "users"),
            created = 1000,
            lastModified = 1000,
        )
        Thread.sleep(10)
        val fav2 = FavoriteQuery(
            id = "fav2",
            name = "Orders Query",
            query = "SELECT * FROM orders",
            tags = listOf("sql", "orders"),
            created = 2000,
            lastModified = 2000,
        )

        repo.save(fav1)
        repo.save(fav2)

        val favorites = repo.getAll()
        assertEquals(2, favorites.size)
        assertEquals("fav2", favorites[0].id)
        assertEquals("fav1", favorites[1].id)
    }

    @Test
    fun `save updates existing favorite and updates lastModified`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        val original = FavoriteQuery(
            id = "fav1",
            name = "Original Name",
            query = "SELECT 1",
            tags = listOf("original"),
            created = 1000,
            lastModified = 1000,
        )
        repo.save(original)

        Thread.sleep(10)

        val updated = original.copy(
            name = "Updated Name",
            tags = listOf("updated"),
        )
        repo.save(updated)

        val favorites = repo.getAll()
        assertEquals(1, favorites.size)
        assertEquals("fav1", favorites[0].id)
        assertEquals("Updated Name", favorites[0].name)
        assertEquals(listOf("updated"), favorites[0].tags)
        assertTrue(favorites[0].lastModified > 1000)
    }

    @Test
    fun `getById returns correct favorite or null`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        val fav = FavoriteQuery(
            id = "fav1",
            name = "Test",
            query = "SELECT 1",
            tags = emptyList(),
        )
        repo.save(fav)

        assertNotNull(repo.getById("fav1"))
        assertEquals("Test", repo.getById("fav1")?.name)
        assertNull(repo.getById("nonexistent"))
    }

    @Test
    fun `delete removes favorite and persists`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        repo.save(FavoriteQuery(id = "fav1", name = "First", query = "SELECT 1", tags = emptyList()))
        repo.save(FavoriteQuery(id = "fav2", name = "Second", query = "SELECT 2", tags = emptyList()))
        repo.save(FavoriteQuery(id = "fav3", name = "Third", query = "SELECT 3", tags = emptyList()))

        assertEquals(3, repo.getAll().size)

        repo.delete("fav2")

        val favorites = repo.getAll()
        assertEquals(2, favorites.size)
        assertNull(repo.getById("fav2"))
        assertNotNull(repo.getById("fav1"))
        assertNotNull(repo.getById("fav3"))
    }

    @Test
    fun `search with blank query returns all favorites`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        repo.save(FavoriteQuery(id = "fav1", name = "First", query = "SELECT 1", tags = emptyList()))
        repo.save(FavoriteQuery(id = "fav2", name = "Second", query = "SELECT 2", tags = emptyList()))

        val results = repo.search("")
        assertEquals(2, results.size)
    }

    @Test
    fun `search matches name case-insensitively`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        repo.save(FavoriteQuery(id = "fav1", name = "Users Query", query = "SELECT 1", tags = emptyList()))
        repo.save(FavoriteQuery(id = "fav2", name = "Orders Query", query = "SELECT 2", tags = emptyList()))
        repo.save(FavoriteQuery(id = "fav3", name = "Products Query", query = "SELECT 3", tags = emptyList()))

        val results = repo.search("users")
        assertEquals(1, results.size)
        assertEquals("fav1", results[0].id)

        val results2 = repo.search("QUERY")
        assertEquals(3, results2.size)
    }

    @Test
    fun `search matches query text case-insensitively`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        repo.save(FavoriteQuery(id = "fav1", name = "A", query = "SELECT * FROM users", tags = emptyList()))
        repo.save(FavoriteQuery(id = "fav2", name = "B", query = "SELECT * FROM orders", tags = emptyList()))

        val results = repo.search("users")
        assertEquals(1, results.size)
        assertEquals("fav1", results[0].id)

        val results2 = repo.search("SELECT")
        assertEquals(2, results2.size)
    }

    @Test
    fun `search matches tags case-insensitively`() {
        val favoritesFile = File(tempDir, "favorites.json")
        val repo = FileFavoritesRepository(favoritesFile)

        repo.save(FavoriteQuery(id = "fav1", name = "A", query = "SELECT 1", tags = listOf("important", "users")))
        repo.save(FavoriteQuery(id = "fav2", name = "B", query = "SELECT 2", tags = listOf("daily", "reports")))
        repo.save(FavoriteQuery(id = "fav3", name = "C", query = "SELECT 3", tags = listOf("archive")))

        val results = repo.search("important")
        assertEquals(1, results.size)
        assertEquals("fav1", results[0].id)

        val results2 = repo.search("DAILY")
        assertEquals(1, results2.size)
        assertEquals("fav2", results2[0].id)
    }

    @Test
    fun `persistence survives repository re-instantiation`() {
        val favoritesFile = File(tempDir, "favorites.json")

        val repo1 = FileFavoritesRepository(favoritesFile)
        repeat(3) { i ->
            repo1.save(
                FavoriteQuery(
                    id = "fav$i",
                    name = "Favorite $i",
                    query = "SELECT $i",
                    tags = listOf("tag$i"),
                ),
            )
        }

        val repo2 = FileFavoritesRepository(favoritesFile)
        val favorites = repo2.getAll()

        assertEquals(3, favorites.size)
    }

    @Test
    fun `save creates parent directory if missing`() {
        val nestedDir = File(tempDir, "nested/dir")
        val favoritesFile = File(nestedDir, "favorites.json")

        assertFalse(nestedDir.exists())

        val repo = FileFavoritesRepository(favoritesFile)
        repo.save(
            FavoriteQuery(
                id = "fav1",
                name = "Test",
                query = "SELECT 1",
                tags = emptyList(),
            ),
        )

        assertTrue(favoritesFile.exists())
        assertEquals(1, repo.getAll().size)
    }
}
