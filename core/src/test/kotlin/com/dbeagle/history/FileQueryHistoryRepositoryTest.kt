package com.dbeagle.history

import com.dbeagle.model.QueryHistoryEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileQueryHistoryRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `add and getAll returns entries in most recent first order`() {
        val historyFile = File(tempDir, "history.json")
        val repo = FileQueryHistoryRepository(historyFile)

        val entry1 = QueryHistoryEntry(
            query = "SELECT * FROM users",
            durationMs = 100,
            connectionProfileId = "profile1"
        )
        Thread.sleep(10)
        val entry2 = QueryHistoryEntry(
            query = "SELECT * FROM orders",
            durationMs = 200,
            connectionProfileId = "profile2"
        )

        repo.add(entry1)
        repo.add(entry2)

        val entries = repo.getAll()
        assertEquals(2, entries.size)
        assertEquals(entry2.query, entries[0].query)
        assertEquals(entry1.query, entries[1].query)
    }

    @Test
    fun `persistence survives repository re-instantiation`() {
        val historyFile = File(tempDir, "history.json")
        
        val repo1 = FileQueryHistoryRepository(historyFile)
        repeat(5) { i ->
            repo1.add(
                QueryHistoryEntry(
                    query = "SELECT $i FROM test",
                    durationMs = (i * 10).toLong(),
                    connectionProfileId = "profile$i"
                )
            )
        }

        val repo2 = FileQueryHistoryRepository(historyFile)
        val entries = repo2.getAll()

        assertEquals(5, entries.size)
        assertEquals("SELECT 4 FROM test", entries[0].query)
        assertEquals("SELECT 0 FROM test", entries[4].query)
    }

    @Test
    fun `clear removes all entries and persists empty state`() {
        val historyFile = File(tempDir, "history.json")
        val repo1 = FileQueryHistoryRepository(historyFile)
        
        repeat(3) { i ->
            repo1.add(
                QueryHistoryEntry(
                    query = "SELECT $i",
                    durationMs = 10,
                    connectionProfileId = "profile"
                )
            )
        }
        
        assertEquals(3, repo1.getAll().size)
        
        repo1.clear()
        assertEquals(0, repo1.getAll().size)

        val repo2 = FileQueryHistoryRepository(historyFile)
        assertEquals(0, repo2.getAll().size)
    }

    @Test
    fun `getAll returns empty list when file does not exist`() {
        val historyFile = File(tempDir, "nonexistent.json")
        assertFalse(historyFile.exists())
        
        val repo = FileQueryHistoryRepository(historyFile)
        assertTrue(historyFile.delete())
        assertFalse(historyFile.exists())
        
        val entries = repo.getAll()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `add creates parent directory if missing`() {
        val nestedDir = File(tempDir, "nested/dir")
        val historyFile = File(nestedDir, "history.json")
        
        assertFalse(nestedDir.exists())
        
        val repo = FileQueryHistoryRepository(historyFile)
        repo.add(
            QueryHistoryEntry(
                query = "SELECT 1",
                durationMs = 10,
                connectionProfileId = "profile"
            )
        )
        
        assertTrue(historyFile.exists())
        assertEquals(1, repo.getAll().size)
    }
}
