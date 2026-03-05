package com.dbeagle.viewmodel

import com.dbeagle.driver.DatabaseDriver
import com.dbeagle.driver.DatabaseDriverRegistry
import com.dbeagle.error.ErrorHandler
import com.dbeagle.model.ConnectionConfig
import com.dbeagle.model.ConnectionProfile
import com.dbeagle.pool.DatabaseConnectionPool
import com.dbeagle.profile.ConnectionProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing connection profiles and their state.
 * Handles profile CRUD operations, connection/disconnection logic, and UI state.
 */
class ConnectionListViewModel(
    private val repository: ConnectionProfileRepository,
) : BaseViewModel() {

    /**
     * UI state for the connection list screen.
     */
    data class ConnectionListUiState(
        val profiles: List<ConnectionProfile> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val connectionError: String? = null,
        val connectionErrorProfile: ConnectionProfile? = null,
        val showDialog: Boolean = false,
        val editingProfile: ConnectionProfile? = null,
    )

    private val _uiState = MutableStateFlow(ConnectionListUiState())
    val uiState: StateFlow<ConnectionListUiState> = _uiState.asStateFlow()

    private var connectingJob: Job? = null

    /**
     * Load all profiles from the repository.
     */
    fun refreshProfiles() {
        viewModelScope.launch {
            try {
                updateStateFlow(_uiState) { it.copy(isLoading = true, error = null) }
                val profiles = repository.loadAll().map { it.copy(encryptedPassword = "") }
                updateStateFlow(_uiState) { it.copy(profiles = profiles, isLoading = false) }
            } catch (e: Exception) {
                updateStateFlow(_uiState) {
                    it.copy(
                        error = "Failed to load profiles: ${e.message}",
                        isLoading = false,
                    )
                }
            }
        }
    }

    /**
     * Save a profile (create or update).
     */
    fun saveProfile(
        profile: ConnectionProfile,
        plaintextPassword: String,
    ) {
        viewModelScope.launch {
            try {
                repository.save(profile, plaintextPassword)
                updateStateFlow(_uiState) { it.copy(showDialog = false) }
                refreshProfiles()
            } catch (e: Exception) {
                updateStateFlow(_uiState) {
                    it.copy(
                        connectionError = ErrorHandler.getConnectionErrorMessage(
                            "Failed to save profile: ${e.message}",
                            e,
                        ),
                        connectionErrorProfile = null,
                        showDialog = false,
                    )
                }
            }
        }
    }

    /**
     * Delete a profile by ID.
     */
    fun deleteProfile(
        profileId: String,
        onSessionClose: suspend (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.delete(profileId)
                onSessionClose(profileId)
            } catch (_: Exception) {
                // Ignore session close errors
            }
            DatabaseConnectionPool.closePool(profileId)
            refreshProfiles()
        }
    }

    /**
     * Connect to a database profile.
     * Handles driver instantiation, connection pooling, schema loading.
     */
    suspend fun connectToProfile(
        profile: ConnectionProfile,
        onStatusTextChanged: (String) -> Unit,
        onSessionOpen: (String, String, DatabaseDriver) -> Unit,
        onSetConnecting: (String?) -> Unit,
    ) {
        var driver: DatabaseDriver? = null
        try {
            val loaded = repository.load(profile.id)
                ?: throw IllegalStateException("Profile not found")

            val prototype = DatabaseDriverRegistry.getDriver(loaded.type)
                ?: throw IllegalStateException(
                    "No driver registered for type: ${loaded.type}",
                )

            driver = try {
                prototype::class.java.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Driver for type ${loaded.type} must have a no-arg constructor",
                    e,
                )
            }

            val password = loaded.encryptedPassword
            val configProfile = loaded.copy(
                options = loaded.options + ("password" to password),
            )

            onStatusTextChanged("Status: Connecting (${loaded.name})")

            withContext(Dispatchers.IO) {
                DatabaseConnectionPool.getConnection(loaded, password).use { _ -> }

                driver!!.connect(ConnectionConfig(profile = configProfile))

                try {
                    driver.getSchema()
                } catch (schemaError: Exception) {
                    try {
                        driver.disconnect()
                    } catch (_: Exception) {
                    } finally {
                        DatabaseConnectionPool.closePool(loaded.id)
                    }
                    throw schemaError
                }
            }

            onSessionOpen(loaded.id, loaded.name, driver!!)

            onStatusTextChanged("Status: Connected (${loaded.name})")
        } catch (_: CancellationException) {
            try {
                driver?.disconnect()
            } catch (_: Exception) {}
            DatabaseConnectionPool.closePool(profile.id)
        } catch (e: Exception) {
            try {
                driver?.disconnect()
            } catch (_: Exception) {
            }
            DatabaseConnectionPool.closePool(profile.id)

            updateStateFlow(_uiState) {
                it.copy(
                    connectionError = ErrorHandler.getConnectionErrorMessage(
                        "Failed to connect: ${e.message}",
                        e,
                    ),
                    connectionErrorProfile = profile,
                )
            }
        } finally {
            onSetConnecting(null)
        }
    }

    /**
     * Start connecting to a profile (launches coroutine and tracks job).
     */
    fun connect(
        profile: ConnectionProfile,
        onStatusTextChanged: (String) -> Unit,
        onSessionOpen: (String, String, DatabaseDriver) -> Unit,
        onSetConnecting: (String?) -> Unit,
    ) {
        if (connectingJob?.isActive == true) return

        onSetConnecting(profile.id)
        connectingJob = viewModelScope.launch {
            connectToProfile(
                profile = profile,
                onStatusTextChanged = onStatusTextChanged,
                onSessionOpen = onSessionOpen,
                onSetConnecting = onSetConnecting,
            )
            if (connectingJob?.isActive != true) {
                connectingJob = null
            }
        }
    }

    /**
     * Cancel ongoing connection attempt.
     */
    fun cancelConnect() {
        connectingJob?.cancel()
    }

    /**
     * Disconnect from a profile.
     */
    fun disconnect(
        profileId: String,
        profileName: String,
        onStatusTextChanged: (String) -> Unit,
        onSessionClose: suspend (String) -> Unit,
        onSetConnecting: (String?) -> Unit,
        onUpdateStatus: () -> Unit,
    ) {
        viewModelScope.launch {
            onSetConnecting(profileId)
            try {
                onStatusTextChanged("Status: Disconnecting ($profileName)")
                onSessionClose(profileId)
            } catch (_: Exception) {
            } finally {
                DatabaseConnectionPool.closePool(profileId)
                onUpdateStatus()
                onSetConnecting(null)
            }
        }
    }

    /**
     * Retry connection after error.
     */
    fun retryConnection(
        onStatusTextChanged: (String) -> Unit,
        onSessionOpen: (String, String, DatabaseDriver) -> Unit,
        onSetConnecting: (String?) -> Unit,
    ) {
        val profile = _uiState.value.connectionErrorProfile ?: return
        clearConnectionError()
        connect(profile, onStatusTextChanged, onSessionOpen, onSetConnecting)
    }

    /**
     * Show the connection dialog (for creating/editing profiles).
     */
    fun showDialog(editingProfile: ConnectionProfile? = null) {
        updateStateFlow(_uiState) {
            it.copy(showDialog = true, editingProfile = editingProfile)
        }
    }

    /**
     * Hide the connection dialog.
     */
    fun hideDialog() {
        updateStateFlow(_uiState) { it.copy(showDialog = false) }
    }

    /**
     * Clear connection error state.
     */
    fun clearConnectionError() {
        updateStateFlow(_uiState) {
            it.copy(connectionError = null, connectionErrorProfile = null)
        }
    }
}
