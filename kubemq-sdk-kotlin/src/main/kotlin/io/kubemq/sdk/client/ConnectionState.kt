package io.kubemq.sdk.client

/**
 * Represents the lifecycle state of a [KubeMQClient] connection.
 *
 * Observe state changes via [KubeMQClient.connectionState]:
 * ```kotlin
 * client.connectionState.collect { state ->
 *     when (state) {
 *         is ConnectionState.Idle -> println("Not yet connected")
 *         is ConnectionState.Connecting -> println("Connecting...")
 *         is ConnectionState.Ready -> println("Connected and ready")
 *         is ConnectionState.Reconnecting -> println("Reconnecting, attempt ${state.attempt}")
 *         is ConnectionState.Closed -> println("Client closed")
 *     }
 * }
 * ```
 *
 * @see KubeMQClient.connectionState
 */
public sealed interface ConnectionState {
    /** Initial state before any connection attempt has been made. */
    public data object Idle : ConnectionState

    /** A connection attempt is in progress. */
    public data object Connecting : ConnectionState

    /** Connected to the broker and ready to send/receive messages. */
    public data object Ready : ConnectionState

    /**
     * The connection was lost and automatic reconnection is in progress.
     *
     * @property attempt The current reconnection attempt number (1-based).
     */
    public data class Reconnecting(val attempt: Int) : ConnectionState

    /** The client has been closed and cannot be reused. */
    public data object Closed : ConnectionState
}
