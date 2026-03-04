package com.dbeagle.pool

import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseConnectionPoolLeakDetectionTest {
    @AfterTest
    fun teardown() {
        DatabaseConnectionPool.closeAllPools()
    }

    @Test
    fun `leak detection emits WARN after 30s and writes evidence`() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS")

        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, ".sisyphus").exists() }
            ?: File(System.getProperty("user.dir"))
        val evidenceDir = File(repoRoot, ".sisyphus/evidence").apply { mkdirs() }
        val evidenceFile = File(evidenceDir, "task-33-leak-detection.txt")

        val profile = ConnectionProfile(
            id = "task-33-leak-${Instant.now().toEpochMilli()}",
            name = "Task 33 Leak Detection",
            type = DatabaseType.SQLite,
            host = "",
            port = 0,
            database = ":memory:",
            username = "",
            encryptedPassword = "",
        )

        val buf = ByteArrayOutputStream()
        val capture = PrintStream(buf, true, StandardCharsets.UTF_8)
        val oldErr = System.err
        val oldOut = System.out

        val opened = mutableListOf<Connection>()
        val leakDetectionThresholdMs = 30_000L
        val sleepMs = 35_000L
        val startMs = System.currentTimeMillis()

        try {
            System.setErr(capture)
            System.setOut(capture)

            repeat(10) {
                opened += DatabaseConnectionPool.getConnection(profile, decryptedPassword = "")
            }

            Thread.sleep(sleepMs)
            val afterSleepLog = buf.toString(StandardCharsets.UTF_8)

            val elapsedMs = System.currentTimeMillis() - startMs
            val fullLog = buf.toString(StandardCharsets.UTF_8)
            val excerpt = extractLeakExcerpt(fullLog)

            evidenceFile.writeText(
                buildString {
                    appendLine("=== Task 33: Connection Leak Detection Evidence ===")
                    appendLine("leakDetectionThresholdMs=$leakDetectionThresholdMs")
                    appendLine("openedConnections=${opened.size}")
                    appendLine("sleptMs=$sleepMs")
                    appendLine("elapsedMs=$elapsedMs")
                    appendLine("repoRoot=${repoRoot.absolutePath}")
                    appendLine()
                    appendLine("--- Captured WARN excerpt (Hikari leak detection) ---")
                    appendLine(excerpt.ifBlank { "(no leak excerpt found)" })
                    appendLine("--- End excerpt ---")
                }
            )

            assertTrue(
                afterSleepLog.contains("Connection leak detection triggered", ignoreCase = true) ||
                    fullLog.contains("Connection leak detection triggered", ignoreCase = true),
                "Expected Hikari leak detection WARN to be emitted after ~30s. Captured log:\n$fullLog"
            )
            assertTrue(evidenceFile.exists(), "Evidence file should exist")
            assertTrue(
                evidenceFile.readText().contains("Connection leak detection triggered", ignoreCase = true),
                "Evidence file should include leak detection WARN line"
            )
        } finally {
            opened.forEach {
                try {
                    it.close()
                } catch (_: Exception) {
                }
            }

            DatabaseConnectionPool.closePool(profile.id)

            System.setErr(oldErr)
            System.setOut(oldOut)
            capture.close()
        }
    }
}

private fun extractLeakExcerpt(log: String): String {
    val lines = log.split('\n')
    val startIdx = lines.indexOfFirst { it.contains("Connection leak detection triggered", ignoreCase = true) }
    if (startIdx < 0) return ""

    val endIdxExclusive = (startIdx + 20).coerceAtMost(lines.size)
    return lines.subList(startIdx, endIdxExclusive).joinToString("\n").trim()
}
