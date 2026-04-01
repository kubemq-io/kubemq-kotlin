package io.kubemq.sdk.retry

import io.kubemq.sdk.common.BackoffComputation
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.observability.KubeMQLogger
import kotlinx.coroutines.delay

internal object RetryExecutor {

    suspend fun <T> execute(
        policy: RetryPolicy,
        logger: KubeMQLogger,
        operationName: String,
        block: suspend (attempt: Int) -> T,
    ): T {
        require(policy.maxAttempts >= 1) { "maxAttempts must be >= 1" }

        var lastException: Throwable? = null

        for (attempt in 1..policy.maxAttempts) {
            try {
                return block(attempt)
            } catch (e: KubeMQException) {
                lastException = e
                if (!shouldRetry(e, policy)) {
                    logger.debug { "[$operationName] Non-retryable error on attempt $attempt: ${e.message}" }
                    throw e
                }
                if (attempt == policy.maxAttempts) {
                    logger.warn { "[$operationName] All $attempt attempts exhausted: ${e.message}" }
                    throw e
                }
                val delayMs = computeBackoff(attempt, policy)
                logger.debug {
                    "[$operationName] Attempt $attempt failed (${e.code}), retrying in ${delayMs}ms"
                }
                delay(delayMs)
            } catch (e: Throwable) {
                // Non-KubeMQ exceptions are not retryable
                logger.debug { "[$operationName] Non-KubeMQ error on attempt $attempt: ${e.message}" }
                throw e
            }
        }

        // Should not be reached, but satisfy the compiler
        throw lastException ?: IllegalStateException("Retry loop exited unexpectedly")
    }

    private fun shouldRetry(e: KubeMQException, policy: RetryPolicy): Boolean {
        return e.isRetryable && e.code in policy.retryableStatusCodes
    }

    internal fun computeBackoff(attempt: Int, policy: RetryPolicy): Long =
        BackoffComputation.compute(
            attempt = attempt,
            initialBackoffMs = policy.initialBackoffMs,
            maxBackoffMs = policy.maxBackoffMs,
            multiplier = policy.multiplier,
            jitter = policy.jitter,
        )
}
