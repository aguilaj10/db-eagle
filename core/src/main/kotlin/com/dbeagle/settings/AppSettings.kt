package com.dbeagle.settings

data class AppSettings(
    val resultLimit: Int = DEFAULT_RESULT_LIMIT,
    val queryTimeoutSeconds: Int = DEFAULT_QUERY_TIMEOUT_SECONDS,
    val connectionTimeoutSeconds: Int = DEFAULT_CONNECTION_TIMEOUT_SECONDS,
    val maxConnections: Int = DEFAULT_MAX_CONNECTIONS
) {
    init {
        require(resultLimit > 0) { "resultLimit must be > 0" }
        require(queryTimeoutSeconds > 0) { "queryTimeoutSeconds must be > 0" }
        require(connectionTimeoutSeconds > 0) { "connectionTimeoutSeconds must be > 0" }
        require(maxConnections > 0) { "maxConnections must be > 0" }
    }

    companion object {
        const val DEFAULT_RESULT_LIMIT = 1000
        const val DEFAULT_QUERY_TIMEOUT_SECONDS = 60
        const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 30
        const val DEFAULT_MAX_CONNECTIONS = 10
    }
}
