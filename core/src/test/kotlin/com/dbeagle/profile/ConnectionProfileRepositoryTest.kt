package com.dbeagle.profile

import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionProfileRepositoryTest {

    private lateinit var settings: Settings
    private lateinit var repository: PreferencesBackedConnectionProfileRepository
    private val testMasterPassword = "test-master-password-123"

    @BeforeTest
    fun setup() {
        settings = MapSettings()

        repository = PreferencesBackedConnectionProfileRepository(
            masterPasswordProvider = MasterPasswordProvider { testMasterPassword },
            settings = settings,
        )
    }

    @Test
    fun `save and load profile with encrypted password`() = runTest {
        val plaintextPassword = "my-secret-password"
        val profile = ConnectionProfile(
            id = "test-id-1",
            name = "Test DB",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "testdb",
            username = "testuser",
            encryptedPassword = "",
            options = mapOf("ssl" to "true"),
        )

        repository.save(profile, plaintextPassword)

        val loaded = repository.load("test-id-1")
        assertNotNull(loaded)
        assertEquals(profile.id, loaded.id)
        assertEquals(profile.name, loaded.name)
        assertEquals(profile.type, loaded.type)
        assertEquals(profile.host, loaded.host)
        assertEquals(profile.port, loaded.port)
        assertEquals(profile.database, loaded.database)
        assertEquals(profile.username, loaded.username)
        assertEquals(plaintextPassword, loaded.encryptedPassword)
        assertEquals(profile.options, loaded.options)
    }

    @Test
    fun `load returns null for non-existent profile`() = runTest {
        val loaded = repository.load("non-existent-id")
        assertNull(loaded)
    }

    @Test
    fun `loadAll returns all saved profiles`() = runTest {
        val profile1 = ConnectionProfile(
            id = "id-1",
            name = "DB 1",
            type = DatabaseType.PostgreSQL,
            host = "host1",
            port = 5432,
            database = "db1",
            username = "user1",
            encryptedPassword = "",
        )
        val profile2 = ConnectionProfile(
            id = "id-2",
            name = "DB 2",
            type = DatabaseType.SQLite,
            host = "host2",
            port = 0,
            database = "db2",
            username = "user2",
            encryptedPassword = "",
        )

        repository.save(profile1, "password1")
        repository.save(profile2, "password2")

        val all = repository.loadAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == "id-1" && it.encryptedPassword == "password1" })
        assertTrue(all.any { it.id == "id-2" && it.encryptedPassword == "password2" })
    }

    @Test
    fun `loadAll returns empty list when no profiles exist`() = runTest {
        val all = repository.loadAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `delete removes profile`() = runTest {
        val profile = ConnectionProfile(
            id = "delete-test",
            name = "Delete Me",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "db",
            username = "user",
            encryptedPassword = "",
        )

        repository.save(profile, "password")
        assertNotNull(repository.load("delete-test"))

        repository.delete("delete-test")
        assertNull(repository.load("delete-test"))
    }

    @Test
    fun `wrong master password throws exception on load`() = runTest {
        val profile = ConnectionProfile(
            id = "encrypted-test",
            name = "Encrypted DB",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "db",
            username = "user",
            encryptedPassword = "",
        )

        repository.save(profile, "secret-password")

        val wrongPasswordRepo = PreferencesBackedConnectionProfileRepository(
            masterPasswordProvider = MasterPasswordProvider { "wrong-master-password" },
            settings = settings,
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            wrongPasswordRepo.load("encrypted-test")
        }
        assertTrue(exception.message?.contains("Decryption failed") ?: false)
    }

    @Test
    fun `password is stored encrypted in preferences`() = runTest {
        val plaintextPassword = "my-plaintext-password"
        val profile = ConnectionProfile(
            id = "encryption-check",
            name = "Encryption Test",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "db",
            username = "user",
            encryptedPassword = "",
        )

        repository.save(profile, plaintextPassword)

        val rawStored = settings.getStringOrNull("encryption-check")
        assertNotNull(rawStored)
        assertFalse(rawStored.contains(plaintextPassword))
        assertTrue(rawStored.contains("encryptedPassword"))
        assertTrue(rawStored.contains("ciphertext"))
        assertTrue(rawStored.contains("iv"))
        assertTrue(rawStored.contains("salt"))
    }

    @Test
    fun `save overwrites existing profile with same id`() = runTest {
        val profile = ConnectionProfile(
            id = "overwrite-test",
            name = "Original Name",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "db",
            username = "user",
            encryptedPassword = "",
        )

        repository.save(profile, "password1")

        val updated = profile.copy(name = "Updated Name")
        repository.save(updated, "password2")

        val loaded = repository.load("overwrite-test")
        assertNotNull(loaded)
        assertEquals("Updated Name", loaded.name)
        assertEquals("password2", loaded.encryptedPassword)
    }

    @Test
    fun `profilesFlow emits updated list on save`() = runTest {
        val profile = ConnectionProfile(
            id = "flow-test-1",
            name = "Flow Test DB",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "testdb",
            username = "testuser",
            encryptedPassword = "",
        )

        repository.save(profile, "password")

        val profiles = repository.profilesFlow.first()
        assertEquals(1, profiles.size)
        assertEquals("flow-test-1", profiles[0].id)
        assertEquals("Flow Test DB", profiles[0].name)
    }

    @Test
    fun `profilesFlow emits updated list on delete`() = runTest {
        val profile1 = ConnectionProfile(
            id = "flow-test-2",
            name = "Flow Test DB 1",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "db1",
            username = "user1",
            encryptedPassword = "",
        )
        val profile2 = ConnectionProfile(
            id = "flow-test-3",
            name = "Flow Test DB 2",
            type = DatabaseType.SQLite,
            host = "localhost",
            port = 0,
            database = "db2",
            username = "user2",
            encryptedPassword = "",
        )

        repository.save(profile1, "password1")
        repository.save(profile2, "password2")

        var profiles = repository.profilesFlow.first()
        assertEquals(2, profiles.size)

        repository.delete("flow-test-2")

        profiles = repository.profilesFlow.first()
        assertEquals(1, profiles.size)
        assertEquals("flow-test-3", profiles[0].id)
    }

    @Test
    fun `profilesFlow emits empty list initially`() = runTest {
        val profiles = repository.profilesFlow.first()
        assertTrue(profiles.isEmpty())
    }
}
