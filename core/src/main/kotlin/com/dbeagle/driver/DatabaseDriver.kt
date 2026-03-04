package com.dbeagle.driver

import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata

interface DatabaseDriver {
    /**
     * Establishes a connection to the database using the provided configuration.
     * May fail if the database is unreachable or credentials are invalid.
     */
    suspend fun connect(config: ConnectionConfig)

    /**
     * Closes the current database connection and releases associated resources.
     */
    suspend fun disconnect()

    /**
     * Executes a query with optional parameters and returns the result.
     * [sql] is the query string; [params] are optional parameter values for prepared statements.
     */
    suspend fun executeQuery(
        sql: String,
        params: List<Any> = emptyList(),
    ): QueryResult

    /**
     * Retrieves complete schema metadata including tables, views, indexes, and foreign keys.
     */
    suspend fun getSchema(): SchemaMetadata

    /**
     * Returns list of table names in the database.
     */
    suspend fun getTables(): List<String>

    /**
     * Retrieves column metadata for the specified table.
     */
    suspend fun getColumns(table: String): List<ColumnMetadata>

    /**
     * Retrieves all foreign key relationships in the database.
     */
    suspend fun getForeignKeys(): List<ForeignKeyRelationship>

    /**
     * Validates the connection by performing a simple test query.
     * Returns true if connection is active; false otherwise.
     */
    suspend fun testConnection(): Boolean

    /**
     * Exposes the set of capabilities supported by this driver.
     */
    fun getCapabilities(): Set<DatabaseCapability>

    /**
     * Returns the driver name (e.g., "PostgreSQL", "SQLite").
     */
    fun getName(): String
}
