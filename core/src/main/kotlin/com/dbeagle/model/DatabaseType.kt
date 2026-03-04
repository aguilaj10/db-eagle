package com.dbeagle.model

import kotlinx.serialization.Serializable

@Serializable
sealed class DatabaseType {
    @Serializable
    object PostgreSQL : DatabaseType()

    @Serializable
    object SQLite : DatabaseType()
}
