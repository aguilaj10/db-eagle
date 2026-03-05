package com.dbeagle.driver

import com.dbeagle.model.ColumnMetadata
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ForeignKeyRelationship
import com.dbeagle.model.QueryResult
import com.dbeagle.model.SchemaMetadata
import com.dbeagle.model.TableMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.PrintWriter
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource

class SQLiteDriver : DatabaseDriver {
    private var config: ConnectionConfig? = null
    private var database: Database? = null
    private var jdbcConnection: Connection? = null
    private var foreignKeysEnabled: Boolean = false

    override suspend fun connect(config: ConnectionConfig) {
        val profile = config.profile
        require(profile.type is com.dbeagle.model.DatabaseType.SQLite) {
            "SQLiteDriver only supports DatabaseType.SQLite"
        }

        this.config = config
        val jdbcUrl = buildJdbcUrl(profile)

        val conn =
            withContext(Dispatchers.IO) {
                DriverManager.getConnection(jdbcUrl)
            }

        jdbcConnection = conn

        withContext(Dispatchers.IO) {
            conn.createStatement().use { st ->
                st.queryTimeout = config.queryTimeoutSeconds
                st.execute("PRAGMA foreign_keys = ON")
            }
        }

        foreignKeysEnabled =
            withContext(Dispatchers.IO) {
                conn.createStatement().use { st ->
                    st.queryTimeout = config.queryTimeoutSeconds
                    st.executeQuery("PRAGMA foreign_keys").use { rs ->
                        rs.next() && rs.getInt(1) == 1
                    }
                }
            }

        database =
            Database.connect(
                datasource = NonClosingConnectionDataSource(conn),
            )

        testConnection()
    }

    override suspend fun disconnect() {
        val conn = jdbcConnection
        database = null
        config = null
        jdbcConnection = null
        foreignKeysEnabled = false

        if (conn != null) {
            withContext(Dispatchers.IO) {
                try {
                    conn.close()
                } catch (_: Exception) {
                }
            }
        }
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
                    schema = "main",
                    columns = getColumns(table),
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
                jdbc
                    .prepareStatement(
                        """
                        SELECT name
                        FROM sqlite_master
                        WHERE type = 'table'
                          AND name NOT LIKE 'sqlite_%'
                        ORDER BY name
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.queryTimeout = cfg.queryTimeoutSeconds
                        stmt.executeQuery().use { rs ->
                            buildList {
                                while (rs.next()) add(rs.getString(1))
                            }
                        }
                    }
            }
        }
    }

    override suspend fun getColumns(table: String): List<ColumnMetadata> {
        val db = database ?: return emptyList()
        val cfg = config!!
        val escaped = escapeSqlitePragmaIdent(table)

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc.createStatement().use { stmt ->
                    stmt.queryTimeout = cfg.queryTimeoutSeconds
                    stmt.executeQuery("PRAGMA table_info('$escaped')").use { rs ->
                        buildList {
                            while (rs.next()) {
                                val name = rs.getString("name")
                                val type = rs.getString("type")
                                val notNull = rs.getInt("notnull")
                                val defaultValue = rs.getString("dflt_value")
                                add(
                                    ColumnMetadata(
                                        name = name,
                                        type = type,
                                        nullable = notNull == 0,
                                        defaultValue = defaultValue,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun getForeignKeys(): List<ForeignKeyRelationship> {
        val db = database ?: return emptyList()
        val cfg = config!!

        val tables = getTables()

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                val fks = mutableListOf<ForeignKeyRelationship>()

                for (t in tables) {
                    val escaped = escapeSqlitePragmaIdent(t)
                    jdbc.createStatement().use { stmt ->
                        stmt.queryTimeout = cfg.queryTimeoutSeconds
                        stmt.executeQuery("PRAGMA foreign_key_list('$escaped')").use { rs ->
                            while (rs.next()) {
                                val toTable = rs.getString("table")
                                val fromColumn = rs.getString("from")
                                val toColumn = rs.getString("to")

                                if (!toTable.isNullOrBlank() && !fromColumn.isNullOrBlank() && !toColumn.isNullOrBlank()) {
                                    fks.add(
                                        ForeignKeyRelationship(
                                            fromTable = t,
                                            fromColumn = fromColumn,
                                            toTable = toTable,
                                            toColumn = toColumn,
                                        ),
                                    )
                                }
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

    override fun getCapabilities(): Set<DatabaseCapability> {
        val base =
            setOf(
                DatabaseCapability.Transactions,
                DatabaseCapability.PreparedStatements,
            )
        return if (foreignKeysEnabled) base + DatabaseCapability.ForeignKeys else base
    }

    override fun getName(): String = "SQLite"

    private suspend fun getViews(): List<String> {
        val db = database ?: return emptyList()
        val cfg = config!!

        return withContext(Dispatchers.IO) {
            transaction(db) {
                val jdbc = connection.connection as Connection
                jdbc
                    .prepareStatement(
                        """
                        SELECT name
                        FROM sqlite_master
                        WHERE type = 'view'
                          AND name NOT LIKE 'sqlite_%'
                        ORDER BY name
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.queryTimeout = cfg.queryTimeoutSeconds
                        stmt.executeQuery().use { rs ->
                            buildList {
                                while (rs.next()) add(rs.getString(1))
                            }
                        }
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
                jdbc
                    .prepareStatement(
                        """
                        SELECT name
                        FROM sqlite_master
                        WHERE type = 'index'
                          AND name NOT LIKE 'sqlite_%'
                          AND name IS NOT NULL
                        ORDER BY name
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.queryTimeout = cfg.queryTimeoutSeconds
                        stmt.executeQuery().use { rs ->
                            buildList {
                                while (rs.next()) {
                                    val name = rs.getString(1)
                                    if (!name.isNullOrBlank()) add(name)
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun buildJdbcUrl(profile: com.dbeagle.model.ConnectionProfile): String = "jdbc:sqlite:${profile.database}"
}

private class NonClosingConnectionDataSource(
    private val underlying: Connection,
) : DataSource {
    override fun getConnection(): Connection = nonClosing(underlying)

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

    private fun nonClosing(conn: Connection): Connection = Proxy.newProxyInstance(
        conn::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        if (method.name == "close" && method.parameterCount == 0) {
            null
        } else {
            method.invoke(conn, *(args ?: emptyArray()))
        }
    } as Connection
}

private fun escapeSqlitePragmaIdent(raw: String): String = raw.replace("'", "''")

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
