package com.dbeagle.viewmodel

import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.profile.ConnectionProfileRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionListViewModelTest {

    private class FakeConnectionProfileRepository : ConnectionProfileRepository {
        private val profiles = mutableMapOf<String, ConnectionProfile>()

        override suspend fun save(profile: ConnectionProfile, plaintextPassword: String) {
            profiles[profile.id] = profile.copy(encryptedPassword = "encrypted_$plaintextPassword")
        }

        override suspend fun load(id: String): ConnectionProfile? = profiles[id]

        override suspend fun loadAll(): List<ConnectionProfile> = profiles.values.toList()

        override suspend fun delete(id: String) {
            profiles.remove(id)
        }

        fun addTestProfile(profile: ConnectionProfile) {
            profiles[profile.id] = profile
        }
    }

    private fun createTestProfile(
        id: String = "test-profile-1",
        name: String = "Test DB",
        type: DatabaseType = DatabaseType.PostgreSQL,
    ): ConnectionProfile = ConnectionProfile(
        id = id,
        name = name,
        type = type,
        host = "localhost",
        port = 5432,
        database = "testdb",
        username = "testuser",
        encryptedPassword = "encrypted_pass",
    )

    @Test
    fun initial_state_shows_loading() {
        val repository = FakeConnectionProfileRepository()
        val viewModel = ConnectionListViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertEquals(emptyList(), state.profiles)
        assertNull(state.error)
        assertFalse(state.showDialog)
    }

    @Test
    fun refresh_profiles_loads_profiles_from_repository() = runBlocking {
        val repository = FakeConnectionProfileRepository()
        val profile1 = createTestProfile(id = "p1", name = "Profile 1")
        val profile2 = createTestProfile(id = "p2", name = "Profile 2")

        repository.addTestProfile(profile1)
        repository.addTestProfile(profile2)

        val viewModel = ConnectionListViewModel(repository)
        viewModel.refreshProfiles()

        kotlinx.coroutines.delay(100)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.profiles.size)
        assertTrue(state.profiles.any { it.id == "p1" && it.name == "Profile 1" })
        assertTrue(state.profiles.any { it.id == "p2" && it.name == "Profile 2" })

        state.profiles.forEach { profile ->
            assertEquals("", profile.encryptedPassword)
        }
    }

    @Test
    fun show_dialog_sets_show_dialog_true_without_editing_profile() {
        val repository = FakeConnectionProfileRepository()
        val viewModel = ConnectionListViewModel(repository)

        viewModel.showDialog()

        val state = viewModel.uiState.value
        assertTrue(state.showDialog)
        assertNull(state.editingProfile)
    }

    @Test
    fun show_dialog_sets_show_dialog_true_with_editing_profile() {
        val repository = FakeConnectionProfileRepository()
        val viewModel = ConnectionListViewModel(repository)
        val profile = createTestProfile()

        viewModel.showDialog(profile)

        val state = viewModel.uiState.value
        assertTrue(state.showDialog)
        assertNotNull(state.editingProfile)
        assertEquals(profile.id, state.editingProfile.id)
    }

    @Test
    fun hide_dialog_sets_show_dialog_false() {
        val repository = FakeConnectionProfileRepository()
        val viewModel = ConnectionListViewModel(repository)

        viewModel.showDialog()
        assertTrue(viewModel.uiState.value.showDialog)

        viewModel.hideDialog()

        val state = viewModel.uiState.value
        assertFalse(state.showDialog)
    }

    @Test
    fun clear_connection_error_clears_error_and_profile() {
        val repository = FakeConnectionProfileRepository()
        val viewModel = ConnectionListViewModel(repository)

        viewModel.clearConnectionError()

        val state = viewModel.uiState.value
        assertNull(state.connectionError)
        assertNull(state.connectionErrorProfile)
    }

    @Test
    fun delete_profile_removes_profile_from_repository() = runBlocking {
        val repository = FakeConnectionProfileRepository()
        val profile1 = createTestProfile(id = "p1", name = "Profile 1")
        val profile2 = createTestProfile(id = "p2", name = "Profile 2")

        repository.addTestProfile(profile1)
        repository.addTestProfile(profile2)

        val viewModel = ConnectionListViewModel(repository)
        viewModel.refreshProfiles()
        kotlinx.coroutines.delay(100)

        assertEquals(2, viewModel.uiState.value.profiles.size)

        val sessionClosedIds = mutableListOf<String>()
        viewModel.deleteProfile("p1") { profileId ->
            sessionClosedIds.add(profileId)
        }

        kotlinx.coroutines.delay(100)

        val state = viewModel.uiState.value
        assertEquals(1, state.profiles.size)
        assertFalse(state.profiles.any { it.id == "p1" })
        assertTrue(state.profiles.any { it.id == "p2" })
        assertEquals(listOf("p1"), sessionClosedIds)
    }

    @Test
    fun save_profile_adds_new_profile_and_refreshes() = runBlocking {
        val repository = FakeConnectionProfileRepository()
        val viewModel = ConnectionListViewModel(repository)

        viewModel.showDialog()
        assertTrue(viewModel.uiState.value.showDialog)

        val newProfile = createTestProfile(id = "new-id", name = "New Profile")
        viewModel.saveProfile(newProfile, "plaintext_password")

        kotlinx.coroutines.delay(100)

        val state = viewModel.uiState.value
        assertFalse(state.showDialog)
        assertEquals(1, state.profiles.size)
        assertTrue(state.profiles.any { it.id == "new-id" && it.name == "New Profile" })
    }

    @Test
    fun save_profile_updates_existing_profile() = runBlocking {
        val repository = FakeConnectionProfileRepository()
        val existingProfile = createTestProfile(id = "existing", name = "Original Name")
        repository.addTestProfile(existingProfile)

        val viewModel = ConnectionListViewModel(repository)
        viewModel.refreshProfiles()
        kotlinx.coroutines.delay(100)

        assertEquals("Original Name", viewModel.uiState.value.profiles.first().name)

        val updatedProfile = existingProfile.copy(name = "Updated Name")
        viewModel.saveProfile(updatedProfile, "new_password")

        kotlinx.coroutines.delay(100)

        val state = viewModel.uiState.value
        assertEquals(1, state.profiles.size)
        assertEquals("Updated Name", state.profiles.first().name)
        assertFalse(state.showDialog)
    }
}
