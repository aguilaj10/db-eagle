package com.dbeagle.ddl

import com.dbeagle.model.SequenceMetadata
import kotlinx.serialization.Serializable

@Serializable
data class SequenceChanges(
    val increment: Long? = null,
    val minValue: Long? = null,
    val maxValue: Long? = null,
    val restart: Long? = null,
)

object SequenceDDLBuilder {

    fun buildCreateSequence(dialect: DDLDialect, sequence: SequenceMetadata): String {
        val quotedName = dialect.quoteIdentifier(sequence.name)

        val parts = mutableListOf<String>()
        parts.add("CREATE SEQUENCE $quotedName")
        parts.add("START WITH ${sequence.startValue}")
        parts.add("INCREMENT BY ${sequence.increment}")
        parts.add("MINVALUE ${sequence.minValue}")
        parts.add("MAXVALUE ${sequence.maxValue}")
        parts.add(if (sequence.cycle) "CYCLE" else "NO CYCLE")

        return parts.joinToString(" ")
    }

    fun buildAlterSequence(dialect: DDLDialect, name: String, changes: SequenceChanges): String {
        val quotedName = dialect.quoteIdentifier(name)

        val parts = mutableListOf<String>()
        parts.add("ALTER SEQUENCE $quotedName")

        val alterations = mutableListOf<String>()
        changes.increment?.let { alterations.add("INCREMENT BY $it") }
        changes.minValue?.let { alterations.add("MINVALUE $it") }
        changes.maxValue?.let { alterations.add("MAXVALUE $it") }
        changes.restart?.let { alterations.add("RESTART WITH $it") }

        return parts[0] + " " + alterations.joinToString(" ")
    }

    fun buildDropSequence(dialect: DDLDialect, name: String, ifExists: Boolean = true): String {
        val quotedName = dialect.quoteIdentifier(name)

        return if (ifExists && dialect.supportsIfExists()) {
            "DROP SEQUENCE IF EXISTS $quotedName"
        } else {
            "DROP SEQUENCE $quotedName"
        }
    }
}
