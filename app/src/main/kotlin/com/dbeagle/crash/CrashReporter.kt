package com.dbeagle.crash

import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * CrashReporter handles uncaught exceptions by logging them to a crash log file.
 * 
 * Usage:
 * - Call CrashReporter.install() early in application startup
 * - Uncaught exceptions will be written to ~/.dbeagle/crash.log
 */
object CrashReporter {
    private val logger = LoggerFactory.getLogger(CrashReporter::class.java)
    private val crashLogPath = File(System.getProperty("user.home"), ".dbeagle/crash.log")
    
    /**
     * Install the uncaught exception handler.
     * Should be called once during application initialization.
     */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
        }
        logger.info("CrashReporter installed - crash logs will be written to: $crashLogPath")
    }
    
    /**
     * Write a crash log entry for the given throwable.
     * Can be called directly for testing or for handling specific errors.
     */
    fun writeCrashLog(throwable: Throwable, threadName: String = Thread.currentThread().name) {
        try {
            // Ensure parent directory exists
            crashLogPath.parentFile?.mkdirs()
            
            // Format the crash report
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val stackTrace = getStackTraceString(throwable)
            
            val crashReport = buildString {
                appendLine("=" .repeat(80))
                appendLine("CRASH REPORT")
                appendLine("=" .repeat(80))
                appendLine("Timestamp: $timestamp")
                appendLine("Thread: $threadName")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message ?: "(no message)"}")
                appendLine()
                appendLine("Stack Trace:")
                appendLine(stackTrace)
                appendLine("=" .repeat(80))
                appendLine()
            }
            
            // Append to crash log file
            crashLogPath.appendText(crashReport)
            
            logger.error("Crash logged to: $crashLogPath", throwable)
        } catch (e: Exception) {
            // Fallback: log to console if file writing fails
            logger.error("Failed to write crash log to file", e)
            logger.error("Original crash:", throwable)
        }
    }
    
    /**
     * Get the crash log file path.
     */
    fun getCrashLogPath(): File = crashLogPath
    
    /**
     * Read the contents of the crash log file.
     * Returns null if the file doesn't exist or can't be read.
     */
    fun readCrashLog(): String? {
        return try {
            if (crashLogPath.exists()) {
                crashLogPath.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to read crash log", e)
            null
        }
    }
    
    private fun handleCrash(thread: Thread, throwable: Throwable) {
        writeCrashLog(throwable, thread.name)
        
        // Re-throw to allow default JVM behavior (e.g., print to stderr, exit)
        throw throwable
    }
    
    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
}
