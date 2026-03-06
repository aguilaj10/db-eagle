package com.dbeagle.driver

import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.IndexMetadata
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.SequenceMetadata
import com.dbeagle.model.TableMetadata
import com.dbeagle.pool.DatabaseConnectionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger

class OracleDriver : DatabaseDriver {
    private var config: ConnectionConfig? = null
    private var decryptedPassword: String? = null
    private var database: Database? = null

    override suspend fun connect(config: ConnectionConfig) {
        val profile = config.profile
        require(profile.type is DatabaseType.Oracle) {
            "OracleDriver only supports DatabaseType.Oracle"
        }

        val password =
            profile.options["password"]
                ?: throw IllegalArgumentException(
                    "Missing plaintext password in ConnectionProfile.options['password']",
                )

        this.config = config
        this.decryptedPassword = password

        buildJdbcUrl(profile)

        database =
            Database.connect(
                datasource = OraclePoolBackedDataSource(profile, password),
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

    override suspend fun executeQuery(
        sql: String,
        params: List<Any>,
    ): QueryResult {
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
                                rows = listOf(mapOf("updatedCount" to updated.toString())),
                            )
                        } else {
                            stmt.resultSet.use { rs ->
                                QueryResult.Success(
                                    columnNames = rs.oracleColumnNames(),
                                    rows = rs.oracleRowsAsStringMaps(),
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
        val currentSchema = getCurrentSchema()
        val tables =
            getTables().map { table ->
                TableMetadata(
                    name = table,
                    schema = currentSchema,
                    columns = getColumns(table),
                    primaryKey = getPrimaryKeyColumns(table),
                )
            }

        val views = getViews()
        val indexes = getIndexes()
        val foreignKeys = getForeignKeys()

        return SchemaMetadata(
            tables = tables,
            views = views,
            indexes = indexes,
            foreignKeys = foreignKeys,
            sequences = getSequences(),
        )
    }

    override suspend fun getTables(): List<String> {
        val db = database ?: return emptyList()
        val currentSchema = getCurrentSchema()
        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getTables(null, currentSchema, "%", arrayOf("TABLE")).use { rs ->
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
        val currentSchema = getCurrentSchema()

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getColumns(null, currentSchema, table, "%").use { rs ->
                    buildList {
                        while (rs.next()) {
                            val name = rs.getString("COLUMN_NAME")
                            val type = rs.getString("TYPE_NAME")
                            val nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls
                            val defaultValue = rs.getString("COLUMN_DEF")
                            add(
                                ColumnMetadata(
                                    name = name,
                                    type = type,
                                    nullable = nullable,
                                    defaultValue = defaultValue,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun getForeignKeys(): List<ForeignKeyRelationship> {
        val db = database ?: return emptyList()
        val currentSchema = getCurrentSchema()

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                val md = jdbc.metaData

                val tableNames =
                    md.getTables(null, currentSchema, "%", arrayOf("TABLE")).use { rs ->
                        buildList {
                            while (rs.next()) add(rs.getString("TABLE_NAME"))
                        }
                    }

                val fks = mutableListOf<ForeignKeyRelationship>()
                for (table in tableNames) {
                    md.getImportedKeys(null, currentSchema, table).use { rs ->
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
                                        toColumn = toColumn,
                                    ),
                                )
                            }
                        }
                    }
                }

                fks.distinct().sortedWith(
                    compareBy<ForeignKeyRelationship> { it.fromTable }
                        .thenBy { it.fromColumn }
                        .thenBy { it.toTable }
                        .thenBy { it.toColumn },
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
                        stmt.executeQuery("SELECT 1 FROM DUAL").use { rs ->
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

    override suspend fun getSequences(): List<SequenceMetadata> {
        val db = database ?: return emptyList()
        val currentSchema = getCurrentSchema()

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection

                jdbc.createStatement().use { stmt ->
                    stmt.executeQuery(
                        """
                        SELECT sequence_name, min_value, max_value, increment_by, last_number, cycle_flag
                        FROM user_sequences
                        ORDER BY sequence_name
                        """.trimIndent(),
                    ).use { rs ->
                        buildList {
                            while (rs.next()) {
                                val name = rs.getString("sequence_name")
                                val minValue = rs.getLong("min_value")
                                val maxValue = rs.getLong("max_value")
                                val increment = rs.getLong("increment_by")
                                val lastNumber = rs.getLong("last_number")
                                val cycleFlag = rs.getString("cycle_flag")
                                val cycle = cycleFlag == "Y"

                                add(
                                    SequenceMetadata(
                                        name = name,
                                        schema = currentSchema,
                                        startValue = lastNumber,
                                        increment = increment,
                                        minValue = minValue,
                                        maxValue = maxValue,
                                        cycle = cycle,
                                        ownedByTable = null,
                                        ownedByColumn = null,
                                    ),
                                )
                            }
                        }.sortedBy { it.name }
                    }
                }
            }
        }
    }

    override suspend fun getIndexDetails(tableName: String): List<IndexMetadata> {
        val db = database ?: return emptyList()
        val currentSchema = getCurrentSchema()

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                val md = jdbc.metaData

                md.getIndexInfo(null, currentSchema, tableName, false, false).use { rs ->
                    val indexMap = mutableMapOf<String, MutableList<Pair<String, Int>>>()
                    while (rs.next()) {
                        val indexName = rs.getString("INDEX_NAME")
                        if (indexName.isNullOrBlank()) continue

                        val columnName = rs.getString("COLUMN_NAME")
                        val ordinal = rs.getInt("ORDINAL_POSITION")
                        val nonUnique = rs.getBoolean("NON_UNIQUE")

                        if (!columnName.isNullOrBlank()) {
                            val entry = indexMap.getOrPut(indexName) { mutableListOf() }
                            entry.add(columnName to ordinal)
                        }
                    }

                    indexMap.map { (indexName, columns) ->
                        IndexMetadata(
                            name = indexName,
                            tableName = tableName,
                            columns = columns.sortedBy { it.second }.map { it.first },
                            unique = false,
                        )
                    }.sortedBy { it.name }
                }
            }
        }
    }

    override fun getCapabilities(): Set<DatabaseCapability> = setOf(
        DatabaseCapability.Transactions,
        DatabaseCapability.PreparedStatements,
        DatabaseCapability.ForeignKeys,
        DatabaseCapability.Schemas,
        DatabaseCapability.Views,
        DatabaseCapability.Indexes,
        DatabaseCapability.Sequences,
    )

    override fun getName(): String = "Oracle"

    private fun getCurrentSchema(): String {
        val db = database ?: return "PUBLIC"
        return try {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT USER FROM DUAL").use { rs ->
                        if (rs.next()) {
                            rs.getString(1) ?: "PUBLIC"
                        } else {
                            "PUBLIC"
                        }
                    }
                }
            }
        } catch (_: Exception) {
            "PUBLIC"
        }
    }

    private suspend fun getViews(): List<String> {
        val db = database ?: return emptyList()
        val currentSchema = getCurrentSchema()
        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getTables(null, currentSchema, "%", arrayOf("VIEW")).use { rs ->
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
        val currentSchema = getCurrentSchema()

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                val tableNames =
                    jdbc.metaData.getTables(null, currentSchema, "%", arrayOf("TABLE")).use { rs ->
                        buildList {
                            while (rs.next()) add(rs.getString("TABLE_NAME"))
                        }
                    }

                val indexNames = mutableSetOf<String>()
                for (table in tableNames) {
                    jdbc.metaData.getIndexInfo(null, currentSchema, table, false, false).use { rs ->
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

    private suspend fun getPrimaryKeyColumns(table: String): List<String> {
        val db = database ?: return emptyList()
        val currentSchema = getCurrentSchema()

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getPrimaryKeys(null, currentSchema, table).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getString("COLUMN_NAME"))
                        }
                    }.sorted()
                }
            }
        }
    }

    private fun buildJdbcUrl(profile: ConnectionProfile): String = "jdbc:oracle:thin:@${profile.host}:${profile.port}:${profile.database}"
}

private class OraclePoolBackedDataSource(
    private val profile: ConnectionProfile,
    private val password: String,
) : javax.sql.DataSource {
    override fun getConnection(): Connection = DatabaseConnectionPool.getConnection(profile, password)

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = getConnection()

    override fun getLogWriter(): PrintWriter? = null

    override fun setLogWriter(out: PrintWriter?) {}

    override fun setLoginTimeout(seconds: Int) {}

    override fun getLoginTimeout(): Int = 0

    override fun getParentLogger(): Logger = Logger
        .getGlobal()

    override fun <T> unwrap(iface: Class<T>?): T = throw SQLFeatureNotSupportedException()

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}

private fun ResultSet.oracleColumnNames(): List<String> {
    val md = metaData
    return (1..md.columnCount).map { md.getColumnLabel(it) }
}

private fun ResultSet.oracleRowsAsStringMaps(): List<Map<String, String>> {
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
