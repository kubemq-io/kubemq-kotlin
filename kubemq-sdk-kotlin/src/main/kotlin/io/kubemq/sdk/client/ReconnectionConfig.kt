package io.kubemq.sdk.client

import io.kubemq.sdk.common.Defaults

/**
 * Configuration for automatic reconnection with exponential backoff.
 *
 * When the connection to the KubeMQ broker is lost, the client will automatically
 * attempt to reconnect using the configured backoff strategy. Set [maxRetries] to `0`
 * (default) for unlimited retries.
 *
 * Example:
 * ```kotlin
 * val client = KubeMQClient.pubSub {
 *     address = "localhost:50000"
 *     reconnection {
 *         initialBackoffMs = 1000
 *         maxBackoffMs = 60_000
 *         multiplier = 2.0
 *         maxRetries = 10
 *         jitter = JitterType.FULL
 *     }
 * }
 * ```
 *
 * @see ClientConfig.reconnection
 * @see JitterType
 */
@KubeMQDsl
public class ReconnectionConfig {
    /** Initial delay in milliseconds before the first reconnection attempt. Must be > 0. Default: `500`. */
    public var initialBackoffMs: Long = Defaults.INITIAL_BACKOFF_MS

    /** Maximum delay in milliseconds between reconnection attempts. Must be >= [initialBackoffMs]. Default: `30000`. */
    public var maxBackoffMs: Long = Defaults.MAX_BACKOFF_MS

    /** Multiplier applied to the backoff after each failed attempt. Must be >= 1.0. Default: `2.0`. */
    public var multiplier: Double = Defaults.BACKOFF_MULTIPLIER

    /** Maximum number of reconnection attempts. Set to `0` for unlimited retries. Default: `0`. */
    public var maxRetries: Int = 0

    /** Jitter strategy applied to backoff delays. Default: [JitterType.FULL]. */
    public var jitter: JitterType = JitterType.FULL

    internal fun validate() {
        require(initialBackoffMs > 0) { "initialBackoffMs must be > 0, got $initialBackoffMs" }
        require(maxBackoffMs >= initialBackoffMs) { "maxBackoffMs ($maxBackoffMs) must be >= initialBackoffMs ($initialBackoffMs)" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0, got $multiplier" }
        require(maxRetries >= 0) { "maxRetries must be >= 0, got $maxRetries" }
    }
}
