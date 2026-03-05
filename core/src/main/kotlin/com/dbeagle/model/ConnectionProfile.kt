package com.dbeagle.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: DatabaseType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val encryptedPassword: String,
    val options: Map<String, String> = emptyMap(),
)
