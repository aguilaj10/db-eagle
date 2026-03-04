package com.dbeagle.error

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ErrorHandler manages user feedback for errors via Snackbar (query errors) and Dialog (connection errors).
 * All errors are logged to a local file for debugging.
 */
object ErrorHandler {
    private val logFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".dbeagle")
        dir.mkdirs()
        File(dir, "error.log")
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Log an error to the local file with timestamp and stack trace.
     */
    private fun logError(
        message: String,
        throwable: Throwable? = null,
    ) {
        try {
            val timestamp = LocalDateTime.now().format(dateFormatter)
            val logEntry =
                buildString {
                    append("[$timestamp] $message\n")
                    if (throwable != null) {
                        val sw = StringWriter()
                        throwable.printStackTrace(PrintWriter(sw))
                        append(sw.toString())
                    }
                    append("\n")
                }
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            // Silently fail if logging fails (don't crash the app)
            e.printStackTrace()
        }
    }

    /**
     * Show a query error as a Snackbar toast.
     * @param snackbarHostState The SnackbarHostState to show the message
     * @param scope The CoroutineScope to launch the snackbar
     * @param message The error message to display
     * @param throwable Optional throwable to log
     */
    fun showQueryError(
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope,
        message: String,
        throwable: Throwable? = null,
    ) {
        logError("Query Error: $message", throwable)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    /**
     * Show a connection error dialog (caller must implement dialog UI).
     * This method logs the error and returns the error message for the dialog.
     * @param message The error message to display
     * @param throwable Optional throwable to log
     * @return The formatted error message for the dialog
     */
    fun getConnectionErrorMessage(
        message: String,
        throwable: Throwable? = null,
    ): String {
        logError("Connection Error: $message", throwable)
        return message
    }

    /**
     * Log a general error without showing UI feedback.
     * @param message The error message to log
     * @param throwable Optional throwable to log
     */
    fun logGeneralError(
        message: String,
        throwable: Throwable? = null,
    ) {
        logError("General Error: $message", throwable)
    }

    /**
     * Show a snackbar with an action button.
     * @param snackbarHostState The SnackbarHostState to show the message
     * @param scope The CoroutineScope to launch the snackbar
     * @param message The message to display
     * @param actionLabel The label for the action button
     * @param onAction The action to perform when the button is clicked
     */
    fun showSnackbarWithAction(
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope,
        message: String,
        actionLabel: String,
        onAction: () -> Unit,
    ) {
        scope.launch {
            val result =
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                onAction()
            }
        }
    }

    /**
     * Clear the error log file.
     */
    fun clearLog() {
        try {
            logFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get the path to the error log file.
     */
    fun getLogPath(): String = logFile.absolutePath
}
