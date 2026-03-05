package com.dbeagle.viewmodel

import com.dbeagle.history.QueryHistoryRepository
import com.dbeagle.model.QueryHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryViewModelTest {

    /**
     * Fake QueryHistoryRepository for testing without file system dependencies.
     */
    private class FakeQueryHistoryRepository : QueryHistoryRepository {
        private val entries = mutableListOf<QueryHistoryEntry>()

        override fun add(entry: QueryHistoryEntry) {
            entries.add(0, entry)
        }

        override fun getAll(): List<QueryHistoryEntry> = entries.toList()

        override fun clear() {
            entries.clear()
        }
    }

    private fun createTestRepository() = FakeQueryHistoryRepository()

    @Test
    fun initialState_loadsFromRepository() {
        val repository = createTestRepository()
        val entry1 = QueryHistoryEntry(
            id = "1",
            query = "SELECT * FROM users",
            timestamp = System.currentTimeMillis(),
            durationMs = 100,
            connectionProfileId = "profile1",
        )
        val entry2 = QueryHistoryEntry(
            id = "2",
            query = "SELECT * FROM products",
            timestamp = System.currentTimeMillis(),
            durationMs = 200,
            connectionProfileId = "profile1",
        )
        repository.add(entry1)
        repository.add(entry2)

        val viewModel = HistoryViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(2, state.entries.size, "Should load all entries from repository")
        assertFalse(state.showClearDialog, "showClearDialog should be false initially")
    }

    @Test
    fun initialState_withEmptyRepository() {
        val viewModel = HistoryViewModel(createTestRepository())

        val state = viewModel.uiState.value
        assertEquals(0, state.entries.size, "Should have no entries initially")
        assertFalse(state.showClearDialog, "showClearDialog should be false initially")
    }

    @Test
    fun showClearDialog_updatesState() {
        val viewModel = HistoryViewModel(createTestRepository())

        assertFalse(viewModel.uiState.value.showClearDialog)

        viewModel.showClearDialog()

        assertTrue(viewModel.uiState.value.showClearDialog, "showClearDialog should be true after calling showClearDialog()")
    }

    @Test
    fun hideClearDialog_updatesState() {
        val viewModel = HistoryViewModel(createTestRepository())

        viewModel.showClearDialog()
        assertTrue(viewModel.uiState.value.showClearDialog)

        viewModel.hideClearDialog()

        assertFalse(viewModel.uiState.value.showClearDialog, "showClearDialog should be false after calling hideClearDialog()")
    }

    @Test
    fun clearHistory_clearsRepositoryAndUpdatesState() {
        val repository = createTestRepository()
        val entry1 = QueryHistoryEntry(
            id = "1",
            query = "SELECT * FROM users",
            timestamp = System.currentTimeMillis(),
            durationMs = 100,
            connectionProfileId = "profile1",
        )
        val entry2 = QueryHistoryEntry(
            id = "2",
            query = "SELECT * FROM products",
            timestamp = System.currentTimeMillis(),
            durationMs = 200,
            connectionProfileId = "profile1",
        )
        repository.add(entry1)
        repository.add(entry2)

        val viewModel = HistoryViewModel(repository)
        assertEquals(2, viewModel.uiState.value.entries.size)

        viewModel.clearHistory()

        assertEquals(0, viewModel.uiState.value.entries.size, "Entries should be cleared")
        assertFalse(viewModel.uiState.value.showClearDialog, "showClearDialog should be false after clear")
        assertEquals(0, repository.getAll().size, "Repository should be cleared")
    }

    @Test
    fun clearHistory_whenDialogIsVisible_closesDialog() {
        val repository = createTestRepository()
        val entry = QueryHistoryEntry(
            id = "1",
            query = "SELECT * FROM users",
            timestamp = System.currentTimeMillis(),
            durationMs = 100,
            connectionProfileId = "profile1",
        )
        repository.add(entry)

        val viewModel = HistoryViewModel(repository)
        viewModel.showClearDialog()
        assertTrue(viewModel.uiState.value.showClearDialog)

        viewModel.clearHistory()

        assertFalse(viewModel.uiState.value.showClearDialog, "Dialog should be closed after clearing")
    }

    @Test
    fun refreshHistory_reloadsFromRepository() {
        val repository = createTestRepository()
        val viewModel = HistoryViewModel(repository)

        assertEquals(0, viewModel.uiState.value.entries.size)

        val newEntry = QueryHistoryEntry(
            id = "1",
            query = "SELECT * FROM test",
            timestamp = System.currentTimeMillis(),
            durationMs = 50,
            connectionProfileId = "profile1",
        )
        repository.add(newEntry)

        viewModel.refreshHistory()

        assertEquals(1, viewModel.uiState.value.entries.size, "Should reload entries from repository")
        assertEquals(newEntry.id, viewModel.uiState.value.entries[0].id)
    }

    @Test
    fun clearHistory_onEmptyHistory_doesNotFail() {
        val viewModel = HistoryViewModel(createTestRepository())

        assertEquals(0, viewModel.uiState.value.entries.size)

        viewModel.clearHistory()

        assertEquals(0, viewModel.uiState.value.entries.size)
        assertFalse(viewModel.uiState.value.showClearDialog)
    }
}
