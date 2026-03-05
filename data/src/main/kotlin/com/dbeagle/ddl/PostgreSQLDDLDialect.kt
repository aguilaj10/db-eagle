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
    
    override fun getTypeName(genericType: ColumnType): String {
        return when (genericType) {
            ColumnType.TEXT -> "VARCHAR"
            ColumnType.INTEGER -> "INTEGER"
            ColumnType.BIGINT -> "BIGINT"
            ColumnType.DECIMAL -> "NUMERIC"
            ColumnType.BOOLEAN -> "BOOLEAN"
            ColumnType.DATE -> "DATE"
            ColumnType.TIMESTAMP -> "TIMESTAMP WITH TIME ZONE"
            ColumnType.BLOB -> "BYTEA"
        }
    }
}
