package io.kubemq.sdk.client

/**
 * Strategy for adding randomness to exponential backoff delays during reconnection.
 *
 * Jitter prevents the "thundering herd" problem when many clients reconnect simultaneously.
 *
 * @see ReconnectionConfig.jitter
 */
public enum class JitterType {
    /** No jitter -- uses the exact computed backoff delay. */
    NONE,

    /** Full jitter -- delay is uniform random between 0 and the computed backoff. */
    FULL,

    /** Equal jitter -- delay is (backoff / 2) + uniform random between 0 and (backoff / 2). */
    EQUAL,
}
