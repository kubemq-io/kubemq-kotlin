package io.kubemq.sdk.burnin.patterns

import io.kubemq.sdk.burnin.PatternConfig
import io.kubemq.sdk.burnin.metrics.Metrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class PatternStatus(
    val name: String,
    val running: Boolean,
    val sent: Long,
    val received: Long,
    val errors: Long,
    val channels: List<String>,
)

abstract class BasePattern(
    val name: String,
    val brokerAddress: String,
    val config: PatternConfig,
) {
    protected val logger = LoggerFactory.getLogger("burnin.$name")
    protected val sent = AtomicLong(0)
    protected val received = AtomicLong(0)
    protected val errors = AtomicLong(0)
    protected val channelNames = mutableListOf<String>()

    @Volatile
    protected var running = false
    protected var scope: CoroutineScope? = null
    private var job: Job? = null

    open fun generateChannelNames(): List<String> {
        return (1..config.channels).map { "burnin-$name-$it" }
    }

    suspend fun start(parentScope: CoroutineScope) {
        if (running) return
        running = true
        sent.set(0)
        received.set(0)
        errors.set(0)
        channelNames.clear()
        channelNames.addAll(generateChannelNames())
        scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
        logger.info("Starting pattern {} with {} channels at rate {}/s", name, config.channels, config.rate)
        try {
            createChannels()
        } catch (e: Exception) {
            logger.warn("Channel creation failed (may already exist): {}", e.message)
        }
        job = scope!!.launch {
            try {
                run()
            } catch (_: CancellationException) {
                // normal shutdown
            } catch (e: Exception) {
                logger.error("Pattern $name failed", e)
            }
        }
    }

    suspend fun stop() {
        if (!running) return
        running = false
        logger.info("Stopping pattern {}", name)
        scope?.cancel()
        scope = null
        job = null
    }

    suspend fun cleanup() {
        try {
            deleteChannels()
        } catch (e: Exception) {
            logger.warn("Channel cleanup failed: {}", e.message)
        }
        closeClients()
    }

    fun status(): PatternStatus = PatternStatus(
        name = name,
        running = running,
        sent = sent.get(),
        received = received.get(),
        errors = errors.get(),
        channels = channelNames.toList(),
    )

    protected abstract suspend fun createChannels()
    protected abstract suspend fun deleteChannels()
    protected abstract suspend fun run()
    protected abstract suspend fun closeClients()

    protected fun recordSent(channel: String, count: Long = 1) {
        sent.addAndGet(count)
        Metrics.sent(name, channel, count)
    }

    protected fun recordReceived(channel: String, count: Long = 1) {
        received.addAndGet(count)
        Metrics.received(name, channel, count)
    }

    protected fun recordError(channel: String, count: Long = 1) {
        errors.addAndGet(count)
        Metrics.error(name, channel, count)
    }

    protected fun recordLatency(channel: String, durationMs: Long) {
        Metrics.recordLatency(name, channel, durationMs)
    }

    protected suspend fun rateLimitedLoop(
        scope: CoroutineScope,
        ratePerSecond: Int,
        block: suspend () -> Unit,
    ) {
        if (ratePerSecond <= 0) return
        val intervalMs = 1000L / ratePerSecond
        while (scope.isActive && running) {
            try {
                block()
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                logger.debug("Rate-limited operation failed: {}", e.message)
            }
            delay(intervalMs)
        }
    }
}
