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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger

class PostgreSQLDriver : DatabaseDriver {
    private var config: ConnectionConfig? = null
    private var decryptedPassword: String? = null
    private var database: Database? = null

    override suspend fun connect(config: ConnectionConfig) {
        val profile = config.profile
        require(profile.type is DatabaseType.PostgreSQL) {
            "PostgreSQLDriver only supports DatabaseType.PostgreSQL"
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
                datasource = PoolBackedDataSource(profile, password),
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
                                    columnNames = rs.columnNames(),
                                    rows = rs.rowsAsStringMaps(),
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
        val tables =
            getTables().map { table ->
                TableMetadata(
                    name = table,
                    schema = "public",
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
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                val md = jdbc.metaData

                val tableNames =
                    md.getTables(null, "public", "%", arrayOf("TABLE")).use { rs ->
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

    override suspend fun getSequences(): List<SequenceMetadata> {
        val db = database ?: return emptyList()
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection

                // First, get basic sequence info from information_schema
                val sequences = jdbc.createStatement().use { stmt ->
                    stmt.executeQuery(
                        """
                        SELECT sequence_name, start_value, increment, minimum_value, maximum_value, cycle_option
                        FROM information_schema.sequences
                        WHERE sequence_schema = 'public'
                        """.trimIndent(),
                    ).use { rs ->
                        buildList {
                            while (rs.next()) {
                                val name = rs.getString("sequence_name")
                                val startValue = rs.getLong("start_value")
                                val increment = rs.getLong("increment")
                                val minValue = rs.getLong("minimum_value")
                                val maxValue = rs.getLong("maximum_value")
                                val cycle = rs.getString("cycle_option") == "YES"

                                add(
                                    Triple(
                                        name,
                                        SequenceMetadata(
                                            name = name,
                                            schema = "public",
                                            startValue = startValue,
                                            increment = increment,
                                            minValue = minValue,
                                            maxValue = maxValue,
                                            cycle = cycle,
                                        ),
                                        mutableMapOf<String, Pair<String, String>>(),
                                    ),
                                )
                            }
                        }
                    }
                }

                // Now, get ownership info from pg_depend
                val ownershipMap = mutableMapOf<String, Pair<String, String>>()
                jdbc.createStatement().use { stmt ->
                    stmt.executeQuery(
                        """
                        SELECT s.relname AS sequence_name,
                               t.relname AS table_name,
                               a.attname AS column_name
                        FROM pg_class s
                        JOIN pg_depend d ON d.objid = s.oid
                        JOIN pg_class t ON d.refobjid = t.oid
                        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
                        WHERE s.relkind = 'S'
                          AND d.deptype = 'a'
                          AND s.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public')
                        """.trimIndent(),
                    ).use { rs ->
                        while (rs.next()) {
                            val seqName = rs.getString("sequence_name")
                            val tableName = rs.getString("table_name")
                            val columnName = rs.getString("column_name")
                            ownershipMap[seqName] = tableName to columnName
                        }
                    }
                }

                // Merge ownership info into sequence metadata
                sequences.map { (name, metadata, _) ->
                    val ownership = ownershipMap[name]
                    metadata.copy(
                        ownedByTable = ownership?.first,
                        ownedByColumn = ownership?.second,
                    )
                }.sortedBy { it.name }
            }
        }
    }

    override suspend fun getIndexDetails(tableName: String): List<IndexMetadata> {
        val db = database ?: return emptyList()
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getIndexInfo(null, "public", tableName, false, false).use { rs ->
                    // Group by index name to handle composite indexes
                    val indexMap = mutableMapOf<String, MutableList<Pair<Int, String>>>()
                    val indexUnique = mutableMapOf<String, Boolean>()
                    val indexType = mutableMapOf<String, String>()

                    while (rs.next()) {
                        val indexName = rs.getString("INDEX_NAME")
                        if (indexName.isNullOrBlank()) continue

                        val columnName = rs.getString("COLUMN_NAME")
                        if (columnName.isNullOrBlank()) continue

                        val ordinalPosition = rs.getShort("ORDINAL_POSITION").toInt()
                        val nonUnique = rs.getBoolean("NON_UNIQUE")
                        val indexTypeValue = rs.getShort("TYPE")

                        indexMap.getOrPut(indexName) { mutableListOf() }
                            .add(ordinalPosition to columnName)

                        indexUnique[indexName] = !nonUnique

                        // Map JDBC index type constants to readable strings
                        indexType[indexName] = when (indexTypeValue.toInt()) {
                            DatabaseMetaData.tableIndexClustered.toInt() -> "CLUSTERED"
                            DatabaseMetaData.tableIndexHashed.toInt() -> "HASHED"
                            DatabaseMetaData.tableIndexOther.toInt() -> "OTHER"
                            else -> "BTREE"
                        }
                    }

                    // Build IndexMetadata objects with properly ordered columns
                    indexMap.map { (name, columns) ->
                        IndexMetadata(
                            name = name,
                            tableName = tableName,
                            columns = columns.sortedBy { it.first }.map { it.second },
                            unique = indexUnique[name] ?: false,
                            type = indexType[name],
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
                val tableNames =
                    jdbc.metaData.getTables(null, "public", "%", arrayOf("TABLE")).use { rs ->
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

    private suspend fun getPrimaryKeyColumns(table: String): List<String> {
        val db = database ?: return emptyList()
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.metaData.getPrimaryKeys(null, "public", table).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getString("COLUMN_NAME"))
                        }
                    }.sorted()
                }
            }
        }
    }

    private fun buildJdbcUrl(profile: ConnectionProfile): String = "jdbc:postgresql://${profile.host}:${profile.port}/${profile.database}"
}

private class PoolBackedDataSource(
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

    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLFeatureNotSupportedException()

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
