package io.kubemq.sdk.observability

import io.kubemq.sdk.client.LogLevel

/**
 * Logging interface used internally by the KubeMQ SDK.
 *
 * The SDK provides SLF4J and no-op implementations. Custom implementations
 * can be used for integration with application-specific logging frameworks.
 *
 * @see LogLevel
 */
public interface KubeMQLogger {
    /**
     * Logs a trace-level message.
     *
     * @param message Lazy message supplier (only evaluated if trace is enabled)
     */
    public fun trace(message: () -> String)

    /**
     * Logs a debug-level message.
     *
     * @param message Lazy message supplier (only evaluated if debug is enabled)
     */
    public fun debug(message: () -> String)

    /**
     * Logs an info-level message.
     *
     * @param message Lazy message supplier (only evaluated if info is enabled)
     */
    public fun info(message: () -> String)

    /**
     * Logs a warn-level message.
     *
     * @param message Lazy message supplier (only evaluated if warn is enabled)
     */
    public fun warn(message: () -> String)

    /**
     * Logs an error-level message with an optional cause.
     *
     * @param message Lazy message supplier (only evaluated if error is enabled)
     * @param cause Optional throwable that caused the error
     */
    public fun error(message: () -> String, cause: Throwable? = null)

    /**
     * Checks whether logging is enabled at the specified level.
     *
     * @param level The log level to check
     * @return `true` if messages at [level] would be logged
     */
    public fun isEnabled(level: LogLevel): Boolean
}
