package com.dbeagle.profile

import com.dbeagle.crypto.CredentialEncryption
import com.dbeagle.crypto.EncryptedData
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

class PreferencesBackedConnectionProfileRepository(
    private val masterPasswordProvider: MasterPasswordProvider,
    private val preferences: Preferences = Preferences.userRoot().node("com.dbeagle.profiles"),
) : ConnectionProfileRepository {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    @Serializable
    private data class StoredProfile(
        val id: String,
        val name: String,
        val typeDiscriminator: String,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val encryptedPassword: EncryptedData,
        val options: Map<String, String>,
    )

    override suspend fun save(
        profile: ConnectionProfile,
        plaintextPassword: String,
    ) {
        val masterPassword = masterPasswordProvider.getMasterPassword()
        val encryptedData = CredentialEncryption.encrypt(plaintextPassword, masterPassword)

        val typeDiscriminator =
            when (profile.type) {
                is DatabaseType.PostgreSQL -> "PostgreSQL"
                is DatabaseType.SQLite -> "SQLite"
            }

        val stored =
            StoredProfile(
                id = profile.id,
                name = profile.name,
                typeDiscriminator = typeDiscriminator,
                host = profile.host,
                port = profile.port,
                database = profile.database,
                username = profile.username,
                encryptedPassword = encryptedData,
                options = profile.options,
            )

        val serialized = json.encodeToString(stored)
        preferences.put(profile.id, serialized)
        preferences.flush()
    }

    override suspend fun load(id: String): ConnectionProfile? {
        val serialized = preferences.get(id, null) ?: return null
        val stored = json.decodeFromString<StoredProfile>(serialized)

        val masterPassword = masterPasswordProvider.getMasterPassword()
        val decryptedPassword = CredentialEncryption.decrypt(stored.encryptedPassword, masterPassword)

        val databaseType =
            when (stored.typeDiscriminator) {
                "PostgreSQL" -> DatabaseType.PostgreSQL
                "SQLite" -> DatabaseType.SQLite
                else -> throw IllegalArgumentException("Unknown database type: ${stored.typeDiscriminator}")
            }

        return ConnectionProfile(
            id = stored.id,
            name = stored.name,
            type = databaseType,
            host = stored.host,
            port = stored.port,
            database = stored.database,
            username = stored.username,
            encryptedPassword = decryptedPassword,
            options = stored.options,
        )
    }

    override suspend fun loadAll(): List<ConnectionProfile> {
        val keys = preferences.keys()
        return keys.mapNotNull { key -> load(key) }
    }

    override suspend fun delete(id: String) {
        preferences.remove(id)
        preferences.flush()
    }
}
