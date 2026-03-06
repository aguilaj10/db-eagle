package com.dbeagle.ddl

object PostgreSQLDDLDialect : DDLDialect {

    override fun quoteIdentifier(name: String): String {
        val escaped = name.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    override fun supportsSequences(): Boolean = true

    override fun supportsAlterColumn(): Boolean = true

    override fun supportsDropColumn(): Boolean = true

    override fun supportsIfExists(): Boolean = true

    override fun getTypeName(genericType: ColumnType): String = when (genericType) {
        ColumnType.TEXT -> "VARCHAR"
        ColumnType.INTEGER -> "INTEGER"
        ColumnType.BIGINT -> "BIGINT"
        ColumnType.DECIMAL -> "NUMERIC"
        ColumnType.BOOLEAN -> "BOOLEAN"
        ColumnType.DATE -> "DATE"
        ColumnType.TIMESTAMP -> "TIMESTAMP WITH TIME ZONE"
        ColumnType.BLOB -> "BYTEA"
        ColumnType.SMALLINT -> "SMALLINT"
        ColumnType.REAL -> "REAL"
        ColumnType.DOUBLE_PRECISION -> "DOUBLE PRECISION"
        ColumnType.UUID -> "UUID"
        ColumnType.JSON -> "JSON"
        ColumnType.JSONB -> "JSONB"
        ColumnType.SERIAL -> "SERIAL"
        ColumnType.SMALLSERIAL -> "SMALLSERIAL"
        ColumnType.BIGSERIAL -> "BIGSERIAL"
    }

    override fun getTypeName(genericType: ColumnType, autoIncrement: Boolean): String {
        if (!autoIncrement) {
            return getTypeName(genericType)
        }
        return when (genericType) {
            ColumnType.INTEGER -> "SERIAL"
            ColumnType.BIGINT -> "BIGSERIAL"
            ColumnType.SMALLINT -> "SMALLSERIAL"
            else -> getTypeName(genericType)
        }
    }
}
