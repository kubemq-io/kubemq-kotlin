package io.kubemq.sdk.client

/**
 * Emitted after a successful automatic reconnection.
 *
 * Collect these events via [KubeMQClient.reconnectionEvents] to monitor reconnection activity.
 *
 * @property attempt The reconnection attempt number that succeeded.
 * @property timestamp The epoch millisecond timestamp of the successful reconnection.
 * @see KubeMQClient.reconnectionEvents
 */
public data class ReconnectionEvent(
    public val attempt: Int,
    public val timestamp: Long,
)
