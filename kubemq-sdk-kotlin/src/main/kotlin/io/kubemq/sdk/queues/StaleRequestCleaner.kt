package io.kubemq.sdk.queues

import io.kubemq.sdk.observability.KubeMQLogger
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class StaleRequestCleaner(
    private val name: String,
    private val logger: KubeMQLogger,
    private val timeoutMs: Long = 60_000L,
    private val cleanupIntervalSeconds: Long = 30L,
) {
    private val executor: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "kubemq-$name-cleanup").apply { isDaemon = true }
        }
    }

    @Volatile
    private var cleanupFuture: ScheduledFuture<*>? = null

    fun start(cleanupAction: Runnable) {
        if (cleanupFuture != null) return
        synchronized(this) {
            if (cleanupFuture != null) return
            cleanupFuture = executor.scheduleAtFixedRate(
                cleanupAction,
                cleanupIntervalSeconds,
                cleanupIntervalSeconds,
                TimeUnit.SECONDS,
            )
        }
    }

    fun stop() {
        val future = cleanupFuture
        if (future != null) {
            future.cancel(false)
            cleanupFuture = null
            executor.shutdown()
        }
    }

    fun <T> cleanupStaleEntries(
        pending: ConcurrentHashMap<String, CompletableDeferred<T>>,
        timestamps: ConcurrentHashMap<String, Long>,
        errorFactory: (String, String) -> T,
        extraCleanup: ((String) -> Unit)? = null,
    ) {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { (key, future) ->
            val ts = timestamps[key]
            if (ts != null && (now - ts) > timeoutMs) {
                future.complete(errorFactory(key, "Request timed out after ${timeoutMs}ms"))
                timestamps.remove(key)
                extraCleanup?.invoke(key)
                logger.warn { "Cleaned up stale $name request: $key" }
                true
            } else {
                false
            }
        }
    }
}
