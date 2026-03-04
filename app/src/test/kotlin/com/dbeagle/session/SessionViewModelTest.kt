package com.dbeagle.session

import com.dbeagle.driver.DatabaseCapability
import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.TableMetadata
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionViewModelTest {
    private class FakeDriver : DatabaseDriver {
        var disconnectedCount: Int = 0

        override suspend fun connect(config: ConnectionConfig) {}

        override suspend fun disconnect() {
            disconnectedCount++
        }

        override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult =
            QueryResult.Error("not used")

        override suspend fun getSchema(): SchemaMetadata = SchemaMetadata(
            tables = emptyList(),
            views = emptyList(),
            indexes = emptyList(),
            foreignKeys = emptyList()
        )

        override suspend fun getTables(): List<String> = emptyList()

        override suspend fun getColumns(table: String): List<ColumnMetadata> = emptyList()

        override suspend fun getForeignKeys(): List<ForeignKeyRelationship> = emptyList()

        override suspend fun testConnection(): Boolean = true

        override fun getCapabilities(): Set<DatabaseCapability> = emptySet()

        override fun getName(): String = "Fake"
    }

    @Test
    fun supports_three_independent_sessions_switching_and_close() = runBlocking {
        val closedPools = mutableListOf<String>()
        val vm = SessionViewModel(closePool = { closedPools.add(it) })

        val d1 = FakeDriver()
        val d2 = FakeDriver()
        val d3 = FakeDriver()

        vm.openSession(profileId = "p1", profileName = "One", driver = d1)
        vm.openSession(profileId = "p2", profileName = "Two", driver = d2)
        vm.openSession(profileId = "p3", profileName = "Three", driver = d3)

        assertEquals(listOf("p1", "p2", "p3"), vm.sessionOrder.value)
        assertEquals(setOf("p1", "p2", "p3"), vm.connectedProfileIds.value)
        assertEquals("p1", vm.activeProfileId.value)

        vm.updateQueryEditorSql("p1", "select 1")
        vm.updateQueryEditorSql("p2", "select 2")
        vm.updateQueryEditorSql("p3", "select 3")

        vm.setActiveProfile("p2")
        assertEquals("p2", vm.activeProfileId.value)
        assertEquals("select 2", vm.sessionStates.value["p2"]?.queryEditorSql)

        vm.setActiveProfile("p3")
        assertEquals("p3", vm.activeProfileId.value)
        assertEquals("select 3", vm.sessionStates.value["p3"]?.queryEditorSql)

        vm.setActiveProfile("p1")
        assertEquals("p1", vm.activeProfileId.value)
        assertEquals("select 1", vm.sessionStates.value["p1"]?.queryEditorSql)

        val result2 = QueryResult.Success(columnNames = listOf("a"), rows = listOf(mapOf("a" to "2")))
        vm.recordQueryResult(profileId = "p2", executedSql = "select 2", result = result2)
        assertNotNull(vm.sessionStates.value["p2"]?.lastQueryResult)
        assertEquals(listOf("a"), vm.sessionStates.value["p2"]?.resultColumns)
        assertEquals(listOf(listOf("2")), vm.sessionStates.value["p2"]?.resultRows)
        assertNull(vm.sessionStates.value["p1"]?.lastQueryResult)
        assertNull(vm.sessionStates.value["p3"]?.lastQueryResult)

        vm.closeSession("p2")

        assertEquals(1, d2.disconnectedCount)
        assertEquals(listOf("p2"), closedPools)

        assertFalse(vm.connectedProfileIds.value.contains("p2"))
        assertTrue(vm.connectedProfileIds.value.contains("p1"))
        assertTrue(vm.connectedProfileIds.value.contains("p3"))
        assertNull(vm.sessionStates.value["p2"])

        assertEquals("p1", vm.activeProfileId.value)

        assertEquals("select 1", vm.sessionStates.value["p1"]?.queryEditorSql)
        assertEquals("select 3", vm.sessionStates.value["p3"]?.queryEditorSql)
        assertEquals(0, d1.disconnectedCount)
        assertEquals(0, d3.disconnectedCount)
    }

    @Test
    fun closing_active_session_selects_first_remaining_as_active() = runBlocking {
        val closedPools = mutableListOf<String>()
        val vm = SessionViewModel(closePool = { closedPools.add(it) })

        val d1 = FakeDriver()
        val d2 = FakeDriver()
        val d3 = FakeDriver()

        vm.openSession(profileId = "p1", profileName = "One", driver = d1)
        vm.openSession(profileId = "p2", profileName = "Two", driver = d2)
        vm.openSession(profileId = "p3", profileName = "Three", driver = d3)

        vm.setActiveProfile("p2")
        assertEquals("p2", vm.activeProfileId.value)

        vm.closeSession("p2")

        assertEquals(1, d2.disconnectedCount)
        assertEquals(listOf("p2"), closedPools)
        assertEquals(listOf("p1", "p3"), vm.sessionOrder.value)
        assertEquals("p1", vm.activeProfileId.value)
    }
}
