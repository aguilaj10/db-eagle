package com.dbeagle.driver

import com.dbeagle.model.DatabaseType

object DataDrivers {
    fun registerAll() {
        DatabaseDriverRegistry.registerDriver(DatabaseType.PostgreSQL, PostgreSQLDriver())
        DatabaseDriverRegistry.registerDriver(DatabaseType.SQLite, SQLiteDriver())
        DatabaseDriverRegistry.registerDriver(DatabaseType.Oracle, OracleDriver())
    }
}
