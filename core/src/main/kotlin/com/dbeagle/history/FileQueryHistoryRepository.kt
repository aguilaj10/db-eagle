package com.dbeagle.history

import com.dbeagle.model.QueryHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileQueryHistoryRepository(
    private val historyFile: File = File(System.getProperty("user.home"), ".dbeagle/history.json"),
) : QueryHistoryRepository {
    private val json = Json { prettyPrint = true }
    private val _historyFlow = MutableStateFlow<List<QueryHistoryEntry>>(emptyList())

    init {
        historyFile.parentFile?.mkdirs()
        if (!historyFile.exists()) {
            historyFile.writeText("[]")
        }
        _historyFlow.value = getAll()
    }

    override fun add(entry: QueryHistoryEntry) {
        val current = getAll().toMutableList()
        current.add(0, entry)
        save(current)
    }

    override fun getAll(): List<QueryHistoryEntry> {
        if (!historyFile.exists()) return emptyList()
        val text = historyFile.readText().trim()
        if (text.isEmpty() || text == "[]") return emptyList()
        return json.decodeFromString<List<QueryHistoryEntry>>(text)
    }

    override fun getAllFlow(): Flow<List<QueryHistoryEntry>> = _historyFlow.asStateFlow()

    override fun clear() {
        save(emptyList())
    }

    private fun save(entries: List<QueryHistoryEntry>) {
        val tempFile = File.createTempFile("history", ".json", historyFile.parentFile)
        try {
            tempFile.writeText(json.encodeToString(entries))
            try {
                // Try atomic move first
                Files.move(tempFile.toPath(), historyFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: UnsupportedOperationException) {
                // Fallback: if atomic move is not supported, use non-atomic move
                Files.move(tempFile.toPath(), historyFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                // Fallback: if atomic move is not supported (filesystem-specific), use non-atomic move
                Files.move(tempFile.toPath(), historyFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            _historyFlow.value = entries
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
