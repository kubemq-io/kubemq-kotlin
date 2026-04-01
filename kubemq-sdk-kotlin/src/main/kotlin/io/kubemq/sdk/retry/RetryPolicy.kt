package io.kubemq.sdk.retry

import io.kubemq.sdk.client.JitterType
import io.kubemq.sdk.client.KubeMQDsl
import io.kubemq.sdk.exception.ErrorCode

/**
 * Configuration for automatic retry of failed operations with exponential backoff.
 *
 * Create using the [retryPolicy] DSL builder:
 * ```kotlin
 * val policy = retryPolicy {
 *     maxAttempts = 5
 *     initialBackoffMs = 1000
 *     maxBackoffMs = 30_000
 *     multiplier = 2.0
 *     jitter = JitterType.FULL
 *     retryableStatusCodes = setOf(ErrorCode.UNAVAILABLE, ErrorCode.DEADLINE_EXCEEDED)
 * }
 * ```
 *
 * @see retryPolicy
 * @see JitterType
 * @see ErrorCode
 */
public class RetryPolicy(
    /** Maximum number of retry attempts. Default: `3`. */
    public val maxAttempts: Int = 3,
    /** Initial backoff delay in milliseconds. Default: `500`. */
    public val initialBackoffMs: Long = 500,
    /** Maximum backoff delay in milliseconds. Default: `30000`. */
    public val maxBackoffMs: Long = 30_000,
    /** Multiplier applied to the backoff after each attempt. Default: `2.0`. */
    public val multiplier: Double = 2.0,
    /** Jitter strategy for randomizing backoff delays. Default: [JitterType.FULL]. */
    public val jitter: JitterType = JitterType.FULL,
    /** Set of [ErrorCode] values that trigger a retry. */
    public val retryableStatusCodes: Set<ErrorCode> = setOf(
        ErrorCode.UNAVAILABLE,
        ErrorCode.DEADLINE_EXCEEDED,
        ErrorCode.RESOURCE_EXHAUSTED,
        ErrorCode.ABORTED,
    ),
)

/**
 * DSL builder for [RetryPolicy].
 *
 * @see retryPolicy
 */
@KubeMQDsl
public class RetryPolicyBuilder {
    /** Maximum number of retry attempts. Default: `3`. */
    public var maxAttempts: Int = 3
    /** Initial backoff delay in milliseconds. Default: `500`. */
    public var initialBackoffMs: Long = 500
    /** Maximum backoff delay in milliseconds. Default: `30000`. */
    public var maxBackoffMs: Long = 30_000
    /** Multiplier applied to the backoff after each attempt. Default: `2.0`. */
    public var multiplier: Double = 2.0
    /** Jitter strategy for randomizing backoff delays. Default: [JitterType.FULL]. */
    public var jitter: JitterType = JitterType.FULL
    /** Set of [ErrorCode] values that trigger a retry. */
    public var retryableStatusCodes: Set<ErrorCode> = setOf(
        ErrorCode.UNAVAILABLE, ErrorCode.DEADLINE_EXCEEDED,
        ErrorCode.RESOURCE_EXHAUSTED, ErrorCode.ABORTED,
    )

    internal fun build(): RetryPolicy = RetryPolicy(
        maxAttempts, initialBackoffMs, maxBackoffMs, multiplier, jitter, retryableStatusCodes,
    )
}

/**
 * Creates a [RetryPolicy] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val policy = retryPolicy {
 *     maxAttempts = 5
 *     initialBackoffMs = 1000
 * }
 * ```
 *
 * @param block Configuration block for [RetryPolicyBuilder]
 * @return Configured [RetryPolicy] instance
 */
public fun retryPolicy(block: RetryPolicyBuilder.() -> Unit): RetryPolicy =
    RetryPolicyBuilder().apply(block).build()
