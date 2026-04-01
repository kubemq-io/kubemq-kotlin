package io.kubemq.sdk.exception

/**
 * Categorizes errors by their expected recovery behavior.
 *
 * @see KubeMQException.category
 */
public enum class ErrorCategory {
    /** The error is temporary and the operation can be retried (e.g., network blip, rate limit). */
    TRANSIENT,

    /** The error is caused by invalid input and will not succeed without changing the request. */
    PERMANENT,

    /** The error is fatal and the client should be recreated (e.g., closed client, data loss). */
    FATAL,
}
