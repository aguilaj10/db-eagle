package com.dbeagle.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base class for ViewModels providing coroutine scope management and StateFlow utilities.
 *
 * All ViewModels in the application should extend this class to get:
 * - Managed coroutine scope with SupervisorJob for fault tolerance
 * - Helper methods for creating and updating StateFlow properties
 * - Centralized cleanup via dispose()
 *
 * Usage pattern:
 * ```
 * class MyViewModel : BaseViewModel() {
 *     private val _uiState = MutableStateFlow(MyUiState())
 *     val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
 *
 *     fun doWork() {
 *         viewModelScope.launch {
 *             // async work
 *         }
 *     }
 *
 *     fun updateState(transform: (MyUiState) -> MyUiState) {
 *         updateStateFlow(_uiState, transform)
 *     }
 * }
 * ```
 */
open class BaseViewModel {

    /**
     * Coroutine scope for ViewModel operations.
     * Uses SupervisorJob so that one failing coroutine doesn't cancel others.
     * Uses Dispatchers.Default for CPU-intensive work.
     */
    protected val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Creates a protected MutableStateFlow with its public StateFlow counterpart.
     *
     * @param initialValue The initial value for the state flow
     * @return A Pair where first is the mutable state (protected) and second is the immutable state (public)
     */
    protected fun <T> stateFlowPair(initialValue: T): Pair<MutableStateFlow<T>, StateFlow<T>> {
        val mutable = MutableStateFlow(initialValue)
        return mutable to mutable.asStateFlow()
    }

    /**
     * Updates a MutableStateFlow safely using a transform function.
     * This is the recommended way to update state in ViewModels.
     *
     * @param mutableStateFlow The state flow to update
     * @param transform Function that takes current state and returns new state
     */
    protected fun <T> updateStateFlow(
        mutableStateFlow: MutableStateFlow<T>,
        transform: (T) -> T,
    ) {
        mutableStateFlow.value = transform(mutableStateFlow.value)
    }

    /**
     * Cleans up the ViewModel by canceling all coroutines.
     * Call this when the ViewModel is no longer needed.
     * Typically called from the owning component's cleanup lifecycle.
     */
    open fun dispose() {
        viewModelScope.cancel()
    }
}
