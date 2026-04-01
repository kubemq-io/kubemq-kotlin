package io.kubemq.sdk.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ConnectionStateMachine {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun transitionTo(newState: ConnectionState): Boolean {
        var transitioned = false
        _state.update { current ->
            if (isValidTransition(current, newState)) {
                transitioned = true
                newState
            } else {
                current
            }
        }
        return transitioned
    }

    private fun isValidTransition(current: ConnectionState, new: ConnectionState): Boolean =
        when (current) {
            is ConnectionState.Idle -> new is ConnectionState.Connecting || new is ConnectionState.Closed
            is ConnectionState.Connecting -> new is ConnectionState.Ready || new is ConnectionState.Reconnecting || new is ConnectionState.Closed
            is ConnectionState.Ready -> new is ConnectionState.Reconnecting || new is ConnectionState.Closed
            is ConnectionState.Reconnecting -> new is ConnectionState.Ready || new is ConnectionState.Closed
            is ConnectionState.Closed -> false
        }

    val isClosed: Boolean get() = _state.value is ConnectionState.Closed
    val isReady: Boolean get() = _state.value is ConnectionState.Ready
}
