package io.kubemq.sdk.observability

/**
 * Metrics interface for recording KubeMQ SDK operational metrics.
 *
 * The SDK provides an OpenTelemetry-based implementation and a no-op default.
 * Implement this interface to integrate with custom metrics backends.
 */
public interface KubeMQMetrics {
    /**
     * Records a message sent event.
     *
     * @param pattern Message pattern (e.g., "events", "queues", "commands")
     * @param channel Target channel name
     * @param sizeBytes Size of the message payload in bytes
     */
    public fun recordMessageSent(pattern: String, channel: String, sizeBytes: Long)

    /**
     * Records a message received event.
     *
     * @param pattern Message pattern
     * @param channel Source channel name
     * @param sizeBytes Size of the message payload in bytes
     */
    public fun recordMessageReceived(pattern: String, channel: String, sizeBytes: Long)

    /**
     * Records the duration of an operation.
     *
     * @param operation Operation name (e.g., "sendCommand", "publishEvent")
     * @param durationMs Duration in milliseconds
     * @param success `true` if the operation succeeded
     */
    public fun recordOperationDuration(operation: String, durationMs: Long, success: Boolean)

    /**
     * Records an error event.
     *
     * @param operation Operation name that failed
     * @param errorCode The error code string
     */
    public fun recordError(operation: String, errorCode: String)

    /**
     * Records a reconnection attempt.
     *
     * @param attempt Reconnection attempt number
     * @param success `true` if the reconnection succeeded
     */
    public fun recordReconnection(attempt: Int, success: Boolean)

    /**
     * Records a change in active subscriptions.
     *
     * @param pattern Subscription pattern (e.g., "events", "commands")
     * @param delta Change in count (+1 for subscribe, -1 for unsubscribe)
     */
    public fun recordActiveSubscription(pattern: String, delta: Int)
}
