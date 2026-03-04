package com.dbeagle.edit

object InlineUpdate {
    private val identRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val selectAllFromTableRegex = Regex(
        pattern = "^\\s*select\\s+\\*\\s+from\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s*;\\s*)?$",
        option = RegexOption.IGNORE_CASE
    )

    fun isSimpleIdentifier(raw: String): Boolean = identRegex.matches(raw)

    fun inferTableNameFromSelectAll(sql: String): String? {
        val m = selectAllFromTableRegex.matchEntire(sql)
        return m?.groupValues?.getOrNull(1)
    }

    data class UpdateStatement(
        val sql: String,
        val params: List<Any>
    )

    fun buildUpdateById(table: String, column: String, value: Any, id: Any): UpdateStatement {
        require(isSimpleIdentifier(table)) { "Unsupported table name for inline update: '$table'" }
        require(isSimpleIdentifier(column)) { "Unsupported column name for inline update: '$column'" }
        val updateSql = "UPDATE $table SET $column = ? WHERE id = ?"
        return UpdateStatement(sql = updateSql, params = listOf(value, id))
    }
}
