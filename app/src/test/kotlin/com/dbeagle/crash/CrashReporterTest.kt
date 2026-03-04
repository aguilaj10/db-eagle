package com.dbeagle.crash

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertContains

class CrashReporterTest {
    
    @BeforeEach
    fun setup() {
        val crashLogFile = CrashReporter.getCrashLogPath()
        if (crashLogFile.exists()) {
            crashLogFile.delete()
        }
    }
    
    @Test
    fun `writeCrashLog creates crash log file with stack trace`() {
        val testException = RuntimeException("Test crash for QA")
        
        CrashReporter.writeCrashLog(testException, "TestThread")
        
        val crashLogPath = CrashReporter.getCrashLogPath()
        assertTrue(crashLogPath.exists(), "Crash log file should exist")
        
        val logContent = crashLogPath.readText()
        assertContains(logContent, "CRASH REPORT")
        assertContains(logContent, "RuntimeException")
        assertContains(logContent, "Test crash for QA")
        assertContains(logContent, "Thread: TestThread")
        assertContains(logContent, "Stack Trace:")
    }
    
    @Test
    fun `readCrashLog returns crash log contents after writing`() {
        val testException = IllegalStateException("Another test crash")
        
        CrashReporter.writeCrashLog(testException, "MainThread")
        
        val crashLog = CrashReporter.readCrashLog()
        assertNotNull(crashLog, "Crash log should not be null")
        assertContains(crashLog, "IllegalStateException")
        assertContains(crashLog, "Another test crash")
    }
    
    @Test
    fun `multiple crashes append to same log file`() {
        val firstException = RuntimeException("First crash")
        val secondException = NullPointerException("Second crash")
        
        CrashReporter.writeCrashLog(firstException, "Thread1")
        CrashReporter.writeCrashLog(secondException, "Thread2")
        
        val crashLog = CrashReporter.readCrashLog()
        assertNotNull(crashLog)
        assertContains(crashLog, "First crash")
        assertContains(crashLog, "Second crash")
        assertContains(crashLog, "RuntimeException")
        assertContains(crashLog, "NullPointerException")
    }
}
