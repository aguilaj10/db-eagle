package com.dbeagle.driver

import kotlinx.serialization.Serializable

/**
 * Represents capabilities and features supported by a database driver.
 * Drivers expose their supported capabilities via [DatabaseDriver.getCapabilities].
 */
@Serializable
sealed class DatabaseCapability {
    @Serializable
    data object Transactions : DatabaseCapability()

    @Serializable
    data object Savepoints : DatabaseCapability()

    @Serializable
    data object PreparedStatements : DatabaseCapability()

    @Serializable
    data object StoredProcedures : DatabaseCapability()

    @Serializable
    data object Views : DatabaseCapability()

    @Serializable
    data object Indexes : DatabaseCapability()

    @Serializable
    data object ForeignKeys : DatabaseCapability()

    @Serializable
    data object Triggers : DatabaseCapability()

    @Serializable
    data object Schemas : DatabaseCapability()

    @Serializable
    data object FullTextSearch : DatabaseCapability()

    @Serializable
    data object BatchInsert : DatabaseCapability()

    @Serializable
    data object Sequences : DatabaseCapability()
}
