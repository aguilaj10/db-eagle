package com.dbeagle.export

import com.dbeagle.driver.SQLiteDriver
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.model.DatabaseType
import com.dbeagle.model.QueryResult
import com.dbeagle.query.QueryExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportIntegrationTest {
    
    private lateinit var driver: SQLiteDriver
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setup() = runBlocking {
        val dbFile = tempDir.resolve("test.db").toFile()
        driver = SQLiteDriver()
        val profile = ConnectionProfile(
            name = "test",
            type = DatabaseType.SQLite,
            host = "",
            port = 0,
            database = dbFile.absolutePath,
            username = "",
            encryptedPassword = ""
        )
        driver.connect(ConnectionConfig(profile = profile))
    }
    
    @AfterEach
    fun tearDown() = runBlocking {
        driver.disconnect()
    }
    
    @Test
    fun `CSV export with 500 rows produces 501 lines`() = runBlocking {
        driver.executeQuery("""
            CREATE TABLE test_data (
                id INTEGER PRIMARY KEY,
                name TEXT,
                value TEXT
            )
        """.trimIndent(), emptyList())
        
        for (i in 1..500) {
            driver.executeQuery(
                "INSERT INTO test_data (id, name, value) VALUES (?, ?, ?)",
                listOf(i, "name_$i", "value_$i")
            )
        }
        
        val queryResult = QueryExecutor(driver).execute("SELECT * FROM test_data")
        assertTrue(queryResult is QueryResult.Success)
        
        val outputFile = tempDir.resolve("export.csv").toFile()
        val exporter = CsvExporter()
        
        exporter.export(outputFile, queryResult, queryResult.resultSet, null)
        
        val lines = outputFile.readLines()
        assertEquals(501, lines.size)
        assertEquals("id,name,value", lines.first())
        assertTrue(lines[1].startsWith("1,name_1,value_1"))
        assertTrue(lines.last().startsWith("500,name_500,value_500"))
    }
    
    @Test
    fun `CSV export escapes quotes and commas correctly`() = runBlocking {
        driver.executeQuery("""
            CREATE TABLE test_data (
                id INTEGER PRIMARY KEY,
                text TEXT
            )
        """.trimIndent(), emptyList())
        
        driver.executeQuery(
            "INSERT INTO test_data (id, text) VALUES (?, ?)",
            listOf(1, "value with, comma")
        )
        driver.executeQuery(
            "INSERT INTO test_data (id, text) VALUES (?, ?)",
            listOf(2, "value with \"quote\"")
        )
        driver.executeQuery(
            "INSERT INTO test_data (id, text) VALUES (?, ?)",
            listOf(3, "value with\nnewline")
        )
        
        val queryResult = QueryExecutor(driver).execute("SELECT * FROM test_data")
        assertTrue(queryResult is QueryResult.Success)
        
        val outputFile = tempDir.resolve("export.csv").toFile()
        val exporter = CsvExporter()
        
        exporter.export(outputFile, queryResult, queryResult.resultSet, null)
        
        val content = outputFile.readText()
        println("CSV Content length: ${content.length}")
        println("CSV Content:\n$content")
        println("---End of content---")
        
        assertTrue(content.contains("id,text"))
        assertTrue(content.contains("\"value with, comma\""))
        assertTrue(content.contains("\"value with \"\"quote\"\"\""))
        assertTrue(content.contains("\"value with\nnewline\""))
    }
    
    @Test
    fun `JSON export produces valid array of objects`() = runBlocking {
        driver.executeQuery("""
            CREATE TABLE test_data (
                id INTEGER PRIMARY KEY,
                name TEXT,
                value TEXT
            )
        """.trimIndent(), emptyList())
        
        for (i in 1..5) {
            driver.executeQuery(
                "INSERT INTO test_data (id, name, value) VALUES (?, ?, ?)",
                listOf(i, "name_$i", "value_$i")
            )
        }
        
        val queryResult = QueryExecutor(driver).execute("SELECT * FROM test_data")
        assertTrue(queryResult is QueryResult.Success)
        
        val outputFile = tempDir.resolve("export.json").toFile()
        val exporter = JsonExporter()
        
        exporter.export(outputFile, queryResult, queryResult.resultSet, null)
        
        val content = outputFile.readText()
        assertTrue(content.startsWith("["))
        assertTrue(content.endsWith("]"))
        assertTrue(content.contains("\"id\": \"1\""))
        assertTrue(content.contains("\"name\": \"name_1\""))
        assertTrue(content.contains("\"value\": \"value_5\""))
    }
    
    @Test
    fun `SQL export produces INSERT statements`() = runBlocking {
        driver.executeQuery("""
            CREATE TABLE test_data (
                id INTEGER PRIMARY KEY,
                name TEXT,
                value TEXT
            )
        """.trimIndent(), emptyList())
        
        for (i in 1..5) {
            driver.executeQuery(
                "INSERT INTO test_data (id, name, value) VALUES (?, ?, ?)",
                listOf(i, "name_$i", "value_$i")
            )
        }
        
        val queryResult = QueryExecutor(driver).execute("SELECT * FROM test_data")
        assertTrue(queryResult is QueryResult.Success)
        
        val outputFile = tempDir.resolve("export.sql").toFile()
        val exporter = SqlExporter()
        
        exporter.export(outputFile, queryResult, queryResult.resultSet, null)
        
        val content = outputFile.readText()
        val lines = content.lines().filter { it.isNotBlank() }
        assertEquals(5, lines.size)
        assertTrue(lines[0].startsWith("INSERT INTO exported_data"))
        assertTrue(lines[0].contains("VALUES ('1', 'name_1', 'value_1')"))
        assertTrue(lines[4].contains("VALUES ('5', 'name_5', 'value_5')"))
    }
    
    @Test
    fun `SQL export escapes single quotes`() = runBlocking {
        driver.executeQuery("""
            CREATE TABLE test_data (
                id INTEGER PRIMARY KEY,
                text TEXT
            )
        """.trimIndent(), emptyList())
        
        driver.executeQuery(
            "INSERT INTO test_data (id, text) VALUES (?, ?)",
            listOf(1, "O'Brien")
        )
        
        val queryResult = QueryExecutor(driver).execute("SELECT * FROM test_data")
        assertTrue(queryResult is QueryResult.Success)
        
        val outputFile = tempDir.resolve("export.sql").toFile()
        val exporter = SqlExporter()
        
        exporter.export(outputFile, queryResult, queryResult.resultSet, null)
        
        val content = outputFile.readText()
        assertTrue(content.contains("'O''Brien'"))
    }
    
    @Test
    fun `Export with progress callback reports row counts`() = runBlocking {
        driver.executeQuery("""
            CREATE TABLE test_data (
                id INTEGER PRIMARY KEY,
                value TEXT
            )
        """.trimIndent(), emptyList())
        
        for (i in 1..100) {
            driver.executeQuery(
                "INSERT INTO test_data (id, value) VALUES (?, ?)",
                listOf(i, "value_$i")
            )
        }
        
        val queryResult = QueryExecutor(driver).execute("SELECT * FROM test_data")
        assertTrue(queryResult is QueryResult.Success)
        
        val outputFile = tempDir.resolve("export.csv").toFile()
        val exporter = CsvExporter()
        
        var lastRowCount = 0
        var callbackInvoked = false
        var doneReceived = false
        
        exporter.export(outputFile, queryResult, queryResult.resultSet) { rowCount, isDone ->
            lastRowCount = rowCount
            callbackInvoked = true
            if (isDone) doneReceived = true
        }
        
        assertTrue(callbackInvoked)
        assertTrue(doneReceived)
        assertEquals(100, lastRowCount)
    }
}
