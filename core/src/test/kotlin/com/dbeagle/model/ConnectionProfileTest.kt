package com.dbeagle.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionProfileTest {

    @Test
    fun testConnectionProfileSerializationRoundtrip() {
        val original = ConnectionProfile(
            id = "test-profile-1",
            name = "Production DB",
            type = DatabaseType.PostgreSQL,
            host = "localhost",
            port = 5432,
            database = "myapp",
            username = "admin",
            encryptedPassword = "encrypted_secret_123",
            options = mapOf("ssl" to "true", "sslmode" to "require")
        )

        val json = Json.encodeToString(ConnectionProfile.serializer(), original)
        val deserialized = Json.decodeFromString(ConnectionProfile.serializer(), json)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.host, deserialized.host)
        assertEquals(original.port, deserialized.port)
        assertEquals(original.database, deserialized.database)
        assertEquals(original.username, deserialized.username)
        assertEquals(original.encryptedPassword, deserialized.encryptedPassword)
        assertEquals(original.options, deserialized.options)
    }

    @Test
    fun testConnectionProfileWithDefaults() {
        val profile = ConnectionProfile(
            name = "Test DB",
            type = DatabaseType.SQLite,
            host = "localhost",
            port = 3306,
            database = "test",
            username = "user",
            encryptedPassword = "pass"
        )

        val json = Json.encodeToString(ConnectionProfile.serializer(), profile)
        val deserialized = Json.decodeFromString(ConnectionProfile.serializer(), json)

        assertEquals(profile.name, deserialized.name)
        assertEquals(emptyMap(), deserialized.options)
    }
}
