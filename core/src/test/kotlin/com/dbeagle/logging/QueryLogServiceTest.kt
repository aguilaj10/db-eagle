package com.dbeagle.logging

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.nio.file.Files

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryLogServiceTest {

    private lateinit var tempDir: File
    private var originalUserHome: String? = null

    @BeforeAll
    fun setupAll() {
        tempDir = Files.createTempDirectory("querylog-test").toFile()
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.absolutePath)
        resetLogFile()
    }

    @AfterAll
    fun teardownAll() {
        originalUserHome?.let { System.setProperty("user.home", it) }
        resetLogFile()
        tempDir.deleteRecursively()
    }

    @BeforeEach
    fun setup() {
        QueryLogService.clearLogs()
    }

    private fun resetLogFile() {
        try {
            val logFileField = QueryLogService::class.java.getDeclaredField("logFile\$delegate")
            logFileField.isAccessible = true

            val dir = File(tempDir, ".dbeagle")
            dir.mkdirs()
            val newLogFile = File(dir, "query.log")

            logFileField.set(QueryLogService, lazy { newLogFile })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun `logQuery creates file and writes properly formatted entry`() {
        val entry = QueryLogEntry(
            timestamp = 1640000000000L,
            sql = "SELECT * FROM users",
            durationMs = 42L,
            status = QueryStatus.SUCCESS,
            rowCount = 10,
            errorMessage = null,
        )

        QueryLogService.logQuery(entry)

        val logFile = File(tempDir, ".dbeagle/query.log")
        assertTrue(logFile.exists())
        val lines = logFile.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("\"timestamp\":1640000000000"))
        assertTrue(lines[0].contains("\"sql\":\"SELECT * FROM users\""))
        assertTrue(lines[0].contains("\"durationMs\":42"))
        assertTrue(lines[0].contains("\"status\":\"SUCCESS\""))
        assertTrue(lines[0].contains("\"rowCount\":10"))
    }

    @Test
    fun `logQuery appends multiple entries in NDJSON format`() {
        val entry1 = QueryLogEntry(
            timestamp = 1640000000000L,
            sql = "SELECT * FROM users",
            durationMs = 42L,
            status = QueryStatus.SUCCESS,
            rowCount = 10,
        )

        val entry2 = QueryLogEntry(
            timestamp = 1640000001000L,
            sql = "INSERT INTO orders VALUES (1)",
            durationMs = 15L,
            status = QueryStatus.SUCCESS,
            rowCount = 1,
        )

        QueryLogService.logQuery(entry1)
        QueryLogService.logQuery(entry2)

        val logFile = File(tempDir, ".dbeagle/query.log")
        val lines = logFile.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("SELECT * FROM users"))
        assertTrue(lines[1].contains("INSERT INTO orders"))
    }

    @Test
    fun `logQuery handles error status with error message`() {
        val entry = QueryLogEntry(
            timestamp = 1640000000000L,
            sql = "SELECT * FROM nonexistent",
            durationMs = 5L,
            status = QueryStatus.ERROR,
            rowCount = null,
            errorMessage = "Table not found",
        )

        QueryLogService.logQuery(entry)

        val logFile = File(tempDir, ".dbeagle/query.log")
        val lines = logFile.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("\"status\":\"ERROR\""))
        assertTrue(lines[0].contains("\"errorMessage\":\"Table not found\""))
    }

    @Test
    fun `getLogs returns empty list for non-existent file`() {
        QueryLogService.clearLogs()

        val logs = QueryLogService.getLogs()

        assertTrue(logs.isEmpty())
    }

    @Test
    fun `getLogs parses NDJSON format correctly`() {
        val entry1 = QueryLogEntry(
            timestamp = 1640000000000L,
            sql = "SELECT * FROM users",
            durationMs = 42L,
            status = QueryStatus.SUCCESS,
            rowCount = 10,
        )

        val entry2 = QueryLogEntry(
            timestamp = 1640000001000L,
            sql = "DELETE FROM orders",
            durationMs = 25L,
            status = QueryStatus.ERROR,
            rowCount = null,
            errorMessage = "Permission denied",
        )

        QueryLogService.logQuery(entry1)
        QueryLogService.logQuery(entry2)

        val logs = QueryLogService.getLogs()

        assertEquals(2, logs.size)

        assertEquals(1640000000000L, logs[0].timestamp)
        assertEquals("SELECT * FROM users", logs[0].sql)
        assertEquals(42L, logs[0].durationMs)
        assertEquals(QueryStatus.SUCCESS, logs[0].status)
        assertEquals(10, logs[0].rowCount)
        assertNull(logs[0].errorMessage)

        assertEquals(1640000001000L, logs[1].timestamp)
        assertEquals("DELETE FROM orders", logs[1].sql)
        assertEquals(25L, logs[1].durationMs)
        assertEquals(QueryStatus.ERROR, logs[1].status)
        assertNull(logs[1].rowCount)
        assertEquals("Permission denied", logs[1].errorMessage)
    }

    @Test
    fun `getLogs skips blank lines`() {
        val entry = QueryLogEntry(
            timestamp = 1640000000000L,
            sql = "SELECT 1",
            durationMs = 1L,
            status = QueryStatus.SUCCESS,
            rowCount = 1,
        )

        QueryLogService.logQuery(entry)

        val logFile = File(tempDir, ".dbeagle/query.log")
        logFile.appendText("\n")
        logFile.appendText("\n")

        val logs = QueryLogService.getLogs()

        assertEquals(1, logs.size)
        assertEquals("SELECT 1", logs[0].sql)
    }

    @Test
    fun `getLogs skips malformed JSON lines`() {
        val entry = QueryLogEntry(
            timestamp = 1640000000000L,
            sql = "SELECT 1",
            durationMs = 1L,
            status = QueryStatus.SUCCESS,
            rowCount = 1,
        )

        QueryLogService.logQuery(entry)

        val logFile = File(tempDir, ".dbeagle/query.log")
        logFile.appendText("{ invalid json }\n")
        logFile.appendText("not json at all\n")

        val entry2 = QueryLogEntry(
            timestamp = 1640000002000L,
            sql = "SELECT 2",
            durationMs = 2L,
            status = QueryStatus.SUCCESS,
            rowCount = 1,
        )
        QueryLogService.logQuery(entry2)

        val logs = QueryLogService.getLogs()

        assertEquals(2, logs.size)
        assertEquals("SELECT 1", logs[0].sql)
        assertEquals("SELECT 2", logs[1].sql)
    }

    @Test
    fun `clearLogs removes all entries`() {
        repeat(3) { i ->
            QueryLogService.logQuery(
                QueryLogEntry(
                    timestamp = 1640000000000L + i,
                    sql = "SELECT $i",
                    durationMs = 10L,
                    status = QueryStatus.SUCCESS,
                    rowCount = 1,
                ),
            )
        }

        val logFile = File(tempDir, ".dbeagle/query.log")
        assertTrue(logFile.exists())
        assertEquals(3, QueryLogService.getLogs().size)

        QueryLogService.clearLogs()

        assertFalse(logFile.exists())
        assertTrue(QueryLogService.getLogs().isEmpty())
    }

    @Test
    fun `clearLogs handles non-existent file gracefully`() {
        QueryLogService.clearLogs()

        val logFile = File(tempDir, ".dbeagle/query.log")
        assertFalse(logFile.exists())

        QueryLogService.clearLogs()

        assertFalse(logFile.exists())
    }

    @Test
    fun `log entry includes all required fields`() {
        val entry = QueryLogEntry(
            timestamp = 1640000000000L,
            sql = "UPDATE users SET name = 'John'",
            durationMs = 123L,
            status = QueryStatus.SUCCESS,
            rowCount = 5,
            errorMessage = null,
        )

        QueryLogService.logQuery(entry)

        val logs = QueryLogService.getLogs()
        assertEquals(1, logs.size)

        val retrieved = logs[0]
        assertEquals(entry.timestamp, retrieved.timestamp)
        assertEquals(entry.sql, retrieved.sql)
        assertEquals(entry.durationMs, retrieved.durationMs)
        assertEquals(entry.status, retrieved.status)
        assertEquals(entry.rowCount, retrieved.rowCount)
        assertEquals(entry.errorMessage, retrieved.errorMessage)
    }

    @Test
    fun `getLogPath returns correct path`() {
        val path = QueryLogService.getLogPath()

        val expectedFile = File(tempDir, ".dbeagle/query.log")
        assertEquals(expectedFile.absolutePath, path)
    }
}
