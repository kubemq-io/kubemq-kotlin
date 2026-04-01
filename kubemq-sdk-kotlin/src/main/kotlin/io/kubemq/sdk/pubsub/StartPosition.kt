package io.kubemq.sdk.pubsub

/**
 * Determines where an events store subscription begins replaying messages.
 *
 * ```kotlin
 * pubsub.subscribeToEventsStore {
 *     channel = "events-store.orders"
 *     startPosition = StartPosition.StartFromFirst
 * }
 * ```
 *
 * @see EventsStoreSubscriptionConfig.startPosition
 */
public sealed interface StartPosition {
    /** Receive only new messages published after the subscription starts. */
    public data object StartNewOnly : StartPosition

    /** Replay all messages from the beginning of the channel. */
    public data object StartFromFirst : StartPosition

    /** Start from the last message in the channel, then receive new ones. */
    public data object StartFromLast : StartPosition

    /**
     * Start replay at a specific sequence number.
     *
     * @property sequence The sequence number to start from (must be > 0).
     */
    public data class StartAtSequence(val sequence: Long) : StartPosition

    /**
     * Start replay at a specific timestamp.
     *
     * @property timestampNanos Epoch timestamp in nanoseconds (must be > 0).
     */
    public data class StartAtTime(val timestampNanos: Long) : StartPosition

    /**
     * Start replay from a time delta (seconds ago from now).
     *
     * @property seconds Number of seconds back from the current time (must be > 0).
     */
    public data class StartAtTimeDelta(val seconds: Long) : StartPosition
}
