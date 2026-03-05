package com.dbeagle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbeagle.crash.CrashReporter
import com.dbeagle.pool.DatabaseConnectionPool
import org.slf4j.LoggerFactory

data class MemoryStats(
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val maxBytes: Long,
) {
    val usedMb: Long get() = usedBytes / (1024L * 1024L)
    val maxMb: Long get() = maxBytes / (1024L * 1024L)
}

fun readMemoryStats(): MemoryStats {
    val rt = Runtime.getRuntime()
    val total = rt.totalMemory()
    val free = rt.freeMemory()
    val used = total - free
    val max = rt.maxMemory()
    return MemoryStats(
        usedBytes = used,
        freeBytes = free,
        totalBytes = total,
        maxBytes = max,
    )
}

@Composable
fun AppBottomBar(
    statusText: String,
    memoryStats: MemoryStats,
    poolStats: DatabaseConnectionPool.PoolStats?,
    onStatusTextChanged: (String) -> Unit,
) {
    val logger = LoggerFactory.getLogger("com.dbeagle.App")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = "Mem: ${memoryStats.usedMb}MB / ${memoryStats.maxMb}MB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )

            val poolText = if (poolStats == null) {
                "Pool: n/a"
            } else {
                "Pool a=${poolStats.active} i=${poolStats.idle} t=${poolStats.total} w=${poolStats.waiting}"
            }
            val poolColor = when {
                poolStats == null -> MaterialTheme.colorScheme.onSurfaceVariant
                poolStats.waiting > 0 -> MaterialTheme.colorScheme.error
                poolStats.total > 0 && poolStats.active == poolStats.total -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }

            Text(
                text = poolText,
                style = MaterialTheme.typography.labelMedium,
                color = poolColor,
            )

            Spacer(Modifier.width(16.dp))

            TextButton(
                onClick = {
                    val crashLog = CrashReporter.readCrashLog()
                    if (crashLog != null) {
                        try {
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            val selection = java.awt.datatransfer.StringSelection(crashLog)
                            clipboard.setContents(selection, selection)
                            onStatusTextChanged("Status: Crash log copied to clipboard")
                            logger.info("User action: Copied crash log to clipboard")
                        } catch (e: Exception) {
                            onStatusTextChanged("Status: Failed to copy to clipboard")
                            logger.warn("Failed to copy crash log to clipboard", e)
                        }
                    } else {
                        onStatusTextChanged("Status: No crash log found")
                        logger.info("User action: Attempted to copy crash log, but none exists")
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Report Issue",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Report Issue",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
