package io.kubemq.sdk.client

import io.kubemq.sdk.common.BackoffComputation
import io.kubemq.sdk.observability.KubeMQLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

internal class ReconnectionManager(
    private val config: ReconnectionConfig,
    private val scope: CoroutineScope,
    private val logger: KubeMQLogger,
) {
    private var reconnectJob: Job? = null

    @Volatile
    private var cancelled = false

    fun startReconnection(
        reconnectAction: suspend () -> Unit,
        pingCheck: suspend () -> Unit,
        onSuccess: suspend () -> Unit,
        onFailed: () -> Unit,
    ) {
        if (reconnectJob?.isActive == true) return
        cancelled = false

        reconnectJob = scope.launch {
            var attempt = 0
            while (!cancelled && (config.maxRetries == 0 || attempt < config.maxRetries)) {
                attempt++
                val delayMs = computeDelay(attempt)
                logger.debug { "Reconnecting attempt $attempt, delay ${delayMs}ms" }
                delay(delayMs)

                try {
                    reconnectAction()
                    pingCheck()
                    onSuccess()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isTlsError(e)) {
                        logger.error({ "Non-retryable TLS error: ${e.message}" }, e)
                        onFailed()
                        return@launch
                    }
                    logger.warn { "Reconnect attempt $attempt failed: ${e.message}" }
                }
            }
            if (!cancelled) onFailed()
        }
    }

    fun cancel() {
        cancelled = true
        reconnectJob?.cancel()
    }

    private fun computeDelay(attempt: Int): Long = BackoffComputation.compute(
        attempt = attempt,
        initialBackoffMs = config.initialBackoffMs,
        maxBackoffMs = config.maxBackoffMs,
        multiplier = config.multiplier,
        jitter = config.jitter,
    )

    private fun isTlsError(e: Exception): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is SSLException || cause is SSLHandshakeException) return true
            if (cause.message?.contains("certificate", ignoreCase = true) == true) return true
            cause = cause.cause
        }
        return false
    }
}
