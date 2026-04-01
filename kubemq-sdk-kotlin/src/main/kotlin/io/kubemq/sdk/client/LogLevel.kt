package io.kubemq.sdk.client

/**
 * Controls the logging verbosity of the KubeMQ SDK.
 *
 * Set via [ClientConfig.logLevel]. The SDK uses SLF4J as its logging backend;
 * configure an SLF4J provider (e.g., Logback, slf4j-simple) to see output.
 *
 * @see ClientConfig.logLevel
 */
public enum class LogLevel {
    /** Most verbose: logs all internal operations including gRPC frame details. */
    TRACE,

    /** Logs detailed diagnostic information useful for debugging. */
    DEBUG,

    /** Logs important operational events (connections, disconnections). Default level. */
    INFO,

    /** Logs potentially harmful situations (reconnection attempts, message replay failures). */
    WARN,

    /** Logs error events that might still allow the application to continue. */
    ERROR,

    /** Disables all SDK logging. */
    OFF,
}
