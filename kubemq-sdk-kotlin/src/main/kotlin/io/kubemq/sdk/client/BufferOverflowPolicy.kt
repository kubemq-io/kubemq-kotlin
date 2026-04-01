package io.kubemq.sdk.client

/**
 * Determines behavior when the message buffer reaches its maximum capacity.
 *
 * @see BufferConfig.overflowPolicy
 */
public enum class BufferOverflowPolicy {
    /** Reject the new message and throw an exception. */
    REJECT,

    /** Drop the oldest buffered message to make room for the new one. */
    DROP_OLDEST,

    /** Drop the new message silently. */
    DROP_NEWEST,

    /** Block the calling coroutine until buffer space becomes available. */
    BLOCK,
}
