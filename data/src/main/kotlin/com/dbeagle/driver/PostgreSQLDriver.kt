package com.dbeagle.driver

import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.TableMetadata
import com.dbeagle.pool.DatabaseConnectionPool
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

class PostgreSQLDriver : DatabaseDriver {
    private var config: ConnectionConfig? = null
    private var decryptedPassword: String? = null
    private var database: Database? = null

    override suspend fun connect(config: ConnectionConfig) {
        val profile = config.profile
        require(profile.type is com.dbeagle.model.DatabaseType.PostgreSQL) {
            "PostgreSQLDriver only supports DatabaseType.PostgreSQL"
        }

        val password = profile.options["password"]
            ?: throw IllegalArgumentException(
                "Missing plaintext password in ConnectionProfile.options['password']"
            )

        this.config = config
        this.decryptedPassword = password

        buildJdbcUrl(profile)

        database = Database.connect(
            datasource = PoolBackedDataSource(profile, password)
        )
        testConnection()
    }

    override suspend fun disconnect() {
        val profileId = config?.profile?.id
        if (profileId != null) {
            DatabaseConnectionPool.closePool(profileId)
        }
        config = null
        decryptedPassword = null
        database = null
    }

    override suspend fun executeQuery(sql: String, params: List<Any>): QueryResult {
        val db = database ?: return QueryResult.Error("Not connected")
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            try {
                transaction(db) {
                    val jdbc = connection.connection as Connection
                    jdbc.prepareStatement(sql).use { stmt ->
                        stmt.queryTimeout = cfg.queryTimeoutSeconds

                        params.forEachIndexed { idx, value ->
                            stmt.setObject(idx + 1, value)
                        }

                        val hasResultSet = stmt.execute()
                        if (!hasResultSet) {
                            val updated = stmt.updateCount
                            QueryResult.Success(
                                columnNames = listOf("updatedCount"),
                                rows = listOf(mapOf("updatedCount" to updated.toString()))
                            )
                        } else {
                            stmt.resultSet.use { rs ->
                                QueryResult.Success(
                                    columnNames = rs.columnNames(),
                                    rows = rs.rowsAsStringMaps()
                                )
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                QueryResult.Error(message = e.message ?: "SQL error", errorCode = e.sqlState)
            } catch (e: Exception) {
                QueryResult.Error(message = e.message ?: "Error")
            }
        }
    }

    override suspend fun getSchema(): SchemaMetadata {
        val tables = getTables().map { table ->
            TableMetadata(
                name = table,
                schema = "public",
                columns = getColumns(table)
            )
        }

        val views = getViews()
        val indexes = getIndexes()
        val foreignKeys = getForeignKeys()

        return SchemaMetadata(
            tables = tables,
            views = views,
            indexes = indexes,
            foreignKeys = foreignKeys
        )
    }

    override suspend fun getTables(): List<String> {
        val db = database ?: return emptyList()
        val cfg = config!!
        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getTables(null, "public", "%", arrayOf("TABLE")).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getString("TABLE_NAME"))
                        }
                    }.sorted()
                }
            }
        }
    }

    override suspend fun getColumns(table: String): List<ColumnMetadata> {
        val db = database ?: return emptyList()
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getColumns(null, "public", table, "%").use { rs ->
                    buildList {
                        while (rs.next()) {
                            val name = rs.getString("COLUMN_NAME")
                            val type = rs.getString("TYPE_NAME")
                            val nullable = rs.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls
                            val defaultValue = rs.getString("COLUMN_DEF")
                            add(
                                ColumnMetadata(
                                    name = name,
                                    type = type,
                                    nullable = nullable,
                                    defaultValue = defaultValue
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun getForeignKeys(): List<ForeignKeyRelationship> {
        val db = database ?: return emptyList()
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                val md = jdbc.metaData

                val tableNames = md.getTables(null, "public", "%", arrayOf("TABLE")).use { rs ->
                    buildList {
                        while (rs.next()) add(rs.getString("TABLE_NAME"))
                    }
                }

                val fks = mutableListOf<ForeignKeyRelationship>()
                for (table in tableNames) {
                    md.getImportedKeys(null, "public", table).use { rs ->
                        while (rs.next()) {
                            val fromTable = rs.getString("FKTABLE_NAME")
                            val fromColumn = rs.getString("FKCOLUMN_NAME")
                            val toTable = rs.getString("PKTABLE_NAME")
                            val toColumn = rs.getString("PKCOLUMN_NAME")

                            if (
                                !fromTable.isNullOrBlank() &&
                                !fromColumn.isNullOrBlank() &&
                                !toTable.isNullOrBlank() &&
                                !toColumn.isNullOrBlank()
                            ) {
                                fks.add(
                                    ForeignKeyRelationship(
                                        fromTable = fromTable,
                                        fromColumn = fromColumn,
                                        toTable = toTable,
                                        toColumn = toColumn
                                    )
                                )
                            }
                        }
                    }
                }

                fks.distinct().sortedWith(
                    compareBy<ForeignKeyRelationship> { it.fromTable }
                        .thenBy { it.fromColumn }
                        .thenBy { it.toTable }
                        .thenBy { it.toColumn }
                )
            }
        }
    }

    override suspend fun testConnection(): Boolean {
        val db = database ?: return false
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            try {
                transaction(db) {
                    val jdbc = connection.connection as Connection
                    jdbc.createStatement().use { stmt ->
                        stmt.queryTimeout = cfg.queryTimeoutSeconds
                        stmt.executeQuery("SELECT 1").use { rs ->
                            rs.next()
                        }
                    }
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun getCapabilities(): Set<DatabaseCapability> = setOf(
        DatabaseCapability.Transactions,
        DatabaseCapability.PreparedStatements,
        DatabaseCapability.ForeignKeys,
        DatabaseCapability.Schemas,
        DatabaseCapability.Views,
        DatabaseCapability.Indexes
    )

    override fun getName(): String = "PostgreSQL"

    private suspend fun getViews(): List<String> {
        val db = database ?: return emptyList()
        val cfg = config!!
        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getTables(null, "public", "%", arrayOf("VIEW")).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getString("TABLE_NAME"))
                        }
                    }.sorted()
                }
            }
        }
    }

    private suspend fun getIndexes(): List<String> {
        val db = database ?: return emptyList()
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                val tableNames = jdbc.metaData.getTables(null, "public", "%", arrayOf("TABLE")).use { rs ->
                    buildList {
                        while (rs.next()) add(rs.getString("TABLE_NAME"))
                    }
                }

                val indexNames = mutableSetOf<String>()
                for (table in tableNames) {
                    jdbc.metaData.getIndexInfo(null, "public", table, false, false).use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("INDEX_NAME")
                            if (!name.isNullOrBlank()) indexNames.add(name)
                        }
                    }
                }

                indexNames.sorted()
            }
        }
    }

    private fun buildJdbcUrl(profile: com.dbeagle.model.ConnectionProfile): String {
        return "jdbc:postgresql://${profile.host}:${profile.port}/${profile.database}"
    }
}

private class PoolBackedDataSource(
    private val profile: com.dbeagle.model.ConnectionProfile,
    private val password: String
) : javax.sql.DataSource {
    override fun getConnection(): Connection = DatabaseConnectionPool.getConnection(profile, password)
    override fun getConnection(username: String?, password: String?): Connection = getConnection()

    override fun getLogWriter(): java.io.PrintWriter? = null
    override fun setLogWriter(out: java.io.PrintWriter?) {}
    override fun setLoginTimeout(seconds: Int) {}
    override fun getLoginTimeout(): Int = 0
    override fun getParentLogger(): java.util.logging.Logger = java.util.logging.Logger.getGlobal()
    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        throw java.sql.SQLFeatureNotSupportedException()
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}

private fun ResultSet.columnNames(): List<String> {
    val md = metaData
    return (1..md.columnCount).map { md.getColumnLabel(it) }
}

private fun ResultSet.rowsAsStringMaps(): List<Map<String, String>> {
    val md = metaData
    val names = (1..md.columnCount).map { md.getColumnLabel(it) }
    val rows = mutableListOf<Map<String, String>>()
    while (next()) {
        val row = LinkedHashMap<String, String>(names.size)
        for ((i, name) in names.withIndex()) {
            val v = getObject(i + 1)
            row[name] = v?.toString() ?: ""
        }
        rows.add(row)
    }
    return rows
}
