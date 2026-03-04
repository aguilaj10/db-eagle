package com.dbeagle.session

import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.QueryResult
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.ui.SchemaTreeNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionViewModel(
    private val closePool: suspend (profileId: String) -> Unit = { DatabaseConnectionPool.closePool(it) }
) {
    data class ColumnCacheEntry(
        val loadedAtMs: Long,
        val columns: List<SchemaTreeNode.Column>
    )

    data class SchemaUiState(
        val isLoading: Boolean = false,
        val loadedAtMs: Long? = null,
        val nodes: List<SchemaTreeNode> = emptyList(),
        val dialogError: String? = null,
        val columnsCache: Map<String, ColumnCacheEntry> = emptyMap()
    )

    data class SessionUiState(
        val profileId: String,
        val profileName: String?,
        val queryEditorSql: String = DEFAULT_SQL,
        val lastExecutedSql: String? = null,
        val lastQueryResult: QueryResult.Success? = null,
        val resultColumns: List<String> = emptyList(),
        val resultRows: List<List<String>> = emptyList(),
        val schema: SchemaUiState = SchemaUiState()
    )

    private val drivers = LinkedHashMap<String, DatabaseDriver>()

    private val _sessionOrder = MutableStateFlow<List<String>>(emptyList())
    val sessionOrder: StateFlow<List<String>> = _sessionOrder.asStateFlow()

    private val _sessionStates = MutableStateFlow<Map<String, SessionUiState>>(emptyMap())
    val sessionStates: StateFlow<Map<String, SessionUiState>> = _sessionStates.asStateFlow()

    private val _connectedProfileIds = MutableStateFlow<Set<String>>(emptySet())
    val connectedProfileIds: StateFlow<Set<String>> = _connectedProfileIds.asStateFlow()

    private val _connectingProfileId = MutableStateFlow<String?>(null)
    val connectingProfileId: StateFlow<String?> = _connectingProfileId.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    fun getDriver(profileId: String): DatabaseDriver? = drivers[profileId]

    fun getActiveDriver(): DatabaseDriver? = _activeProfileId.value?.let(drivers::get)

    fun setConnecting(profileId: String?) {
        _connectingProfileId.value = profileId
    }

    fun openSession(profileId: String, profileName: String?, driver: DatabaseDriver) {
        val wasEmpty = drivers.isEmpty()
        drivers[profileId] = driver

        val prev = _sessionStates.value[profileId]
        val next = prev?.copy(profileName = profileName) ?: SessionUiState(
            profileId = profileId,
            profileName = profileName
        )
        _sessionStates.value = _sessionStates.value + (profileId to next)

        if (!_sessionOrder.value.contains(profileId)) {
            _sessionOrder.value = _sessionOrder.value + profileId
        }
        _connectedProfileIds.value = drivers.keys.toSet()

        val currentActive = _activeProfileId.value
        if (wasEmpty || currentActive == null || !drivers.containsKey(currentActive)) {
            _activeProfileId.value = profileId
        }
    }

    fun setActiveProfile(profileId: String?) {
        val normalized = profileId?.takeIf { drivers.containsKey(it) }
        _activeProfileId.value = normalized
    }

    suspend fun closeSession(profileId: String) {
        val driver = drivers.remove(profileId)
        if (driver != null) {
            try {
                driver.disconnect()
            } catch (_: Exception) {
            }
        }

        try {
            closePool(profileId)
        } catch (_: Exception) {
        }

        _sessionStates.value = _sessionStates.value - profileId
        _sessionOrder.value = _sessionOrder.value.filterNot { it == profileId }
        _connectedProfileIds.value = drivers.keys.toSet()

        if (_activeProfileId.value == profileId) {
            _activeProfileId.value = _sessionOrder.value.firstOrNull()
        }
    }

    fun updateQueryEditorSql(profileId: String, sql: String) {
        updateSession(profileId) { it.copy(queryEditorSql = sql) }
    }

    fun recordQueryResult(profileId: String, executedSql: String, result: QueryResult.Success) {
        val columns = result.columnNames
        val rows = result.rows.map { rowMap -> columns.map { col -> rowMap[col] ?: "" } }
        updateSession(profileId) {
            it.copy(
                lastExecutedSql = executedSql,
                lastQueryResult = result,
                resultColumns = columns,
                resultRows = rows
            )
        }
    }

    fun clearQueryResult(profileId: String) {
        updateSession(profileId) {
            it.copy(
                lastExecutedSql = null,
                lastQueryResult = null,
                resultColumns = emptyList(),
                resultRows = emptyList()
            )
        }
    }

    fun updateSchemaState(profileId: String, transform: (SchemaUiState) -> SchemaUiState) {
        updateSession(profileId) { s -> s.copy(schema = transform(s.schema)) }
    }

    private fun updateSession(profileId: String, transform: (SessionUiState) -> SessionUiState) {
        val current = _sessionStates.value[profileId] ?: return
        _sessionStates.value = _sessionStates.value + (profileId to transform(current))
    }

    companion object {
        const val DEFAULT_SQL: String = "SELECT * FROM users;\n"
    }
}
