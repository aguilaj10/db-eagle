package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionConfig(
    val profile: ConnectionProfile,
    val connectionTimeoutSeconds: Int = 30,
    val queryTimeoutSeconds: Int = 60
)
