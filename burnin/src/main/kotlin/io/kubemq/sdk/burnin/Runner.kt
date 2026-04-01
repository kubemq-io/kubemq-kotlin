package io.kubemq.sdk.burnin

import io.kubemq.sdk.burnin.metrics.Metrics
import io.kubemq.sdk.burnin.patterns.BasePattern
import io.kubemq.sdk.burnin.patterns.CommandsPattern
import io.kubemq.sdk.burnin.patterns.EventsPattern
import io.kubemq.sdk.burnin.patterns.EventsStorePattern
import io.kubemq.sdk.burnin.patterns.PatternStatus
import io.kubemq.sdk.burnin.patterns.QueriesPattern
import io.kubemq.sdk.burnin.patterns.QueueSimplePattern
import io.kubemq.sdk.burnin.patterns.QueueStreamPattern
import io.kubemq.sdk.burnin.report.Reporter
import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

enum class RunState {
    IDLE, STARTING, RUNNING, STOPPING, COMPLETED, FAILED
}

@Serializable
data class RunStatusResponse(
    val state: String,
    @kotlinx.serialization.SerialName("run_id") val runId: String,
    @kotlinx.serialization.SerialName("started_at") val startedAt: String,
    @kotlinx.serialization.SerialName("elapsed_seconds") val elapsedSeconds: Long,
    val patterns: List<PatternStatus>,
    val verdict: String = "",
)

class Runner(private val startupBrokerAddress: String) {

    private val logger = LoggerFactory.getLogger("burnin.runner")
    private val state = AtomicReference(RunState.IDLE)
    private var runConfig: RunConfig? = null
    private var runScope: CoroutineScope? = null
    private var timerJob: Job? = null
    private var patterns: List<BasePattern> = emptyList()
    private var startedAt: Instant = Instant.EPOCH
    private var runId: String = ""
    private var lastVerdict: String = ""

    val currentState: RunState get() = state.get()

    suspend fun start(config: RunConfig): Result<String> {
        if (!state.compareAndSet(RunState.IDLE, RunState.STARTING) &&
            !state.compareAndSet(RunState.COMPLETED, RunState.STARTING) &&
            !state.compareAndSet(RunState.FAILED, RunState.STARTING)
        ) {
            return Result.failure(IllegalStateException("Cannot start: current state is ${state.get()}"))
        }

        val errors = config.validate()
        if (errors.isNotEmpty()) {
            state.set(RunState.IDLE)
            return Result.failure(IllegalArgumentException("Validation errors: ${errors.joinToString("; ")}"))
        }

        runConfig = config
        runId = config.runId.ifBlank { "run-${System.currentTimeMillis()}" }
        startedAt = Instant.now()
        lastVerdict = ""
        Metrics.reset()

        val brokerAddress = config.effectiveBrokerAddress(startupBrokerAddress)
        logger.info("Starting burn-in run {} targeting {}", runId, brokerAddress)

        // Verify broker connectivity
        try {
            val pingClient = KubeMQClient.pubSub { address = brokerAddress; clientId = "burnin-ping" }
            pingClient.ping()
            pingClient.close()
        } catch (e: Exception) {
            state.set(RunState.FAILED)
            return Result.failure(IllegalStateException("Cannot connect to broker at $brokerAddress: ${e.message}"))
        }

        patterns = buildPatterns(brokerAddress, config)

        runScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            for (pattern in patterns) {
                pattern.start(runScope!!)
            }
            state.set(RunState.RUNNING)
        } catch (e: Exception) {
            state.set(RunState.FAILED)
            logger.error("Failed to start patterns", e)
            return Result.failure(e)
        }

        // Duration timer
        val durationMs = parseDuration(config.duration)
        if (durationMs > 0) {
            timerJob = runScope!!.launch {
                delay(durationMs)
                logger.info("Duration elapsed, stopping run")
                stopInternal()
            }
        }

        return Result.success(runId)
    }

    suspend fun stop(): Result<String> {
        if (state.get() != RunState.RUNNING && state.get() != RunState.STARTING) {
            return Result.failure(IllegalStateException("Not running"))
        }
        stopInternal()
        return Result.success("stopped")
    }

    private suspend fun stopInternal() {
        state.set(RunState.STOPPING)
        timerJob?.cancel()
        for (pattern in patterns) {
            try {
                pattern.stop()
            } catch (e: Exception) {
                logger.warn("Error stopping pattern {}: {}", pattern.name, e.message)
            }
        }
        lastVerdict = Reporter.generateVerdict(patterns, runConfig)
        state.set(RunState.COMPLETED)
        logger.info("Run {} completed. Verdict: {}", runId, lastVerdict)
    }

    suspend fun cleanup(): Result<String> {
        for (pattern in patterns) {
            try {
                pattern.cleanup()
            } catch (e: Exception) {
                logger.warn("Cleanup error for {}: {}", pattern.name, e.message)
            }
        }
        runScope?.cancel()
        runScope = null
        patterns = emptyList()
        return Result.success("cleaned up")
    }

    fun status(): RunStatusResponse {
        val elapsed = if (startedAt != Instant.EPOCH) {
            (Instant.now().epochSecond - startedAt.epochSecond)
        } else {
            0L
        }
        return RunStatusResponse(
            state = state.get().name.lowercase(),
            runId = runId,
            startedAt = startedAt.toString(),
            elapsedSeconds = elapsed,
            patterns = patterns.map { it.status() },
            verdict = lastVerdict,
        )
    }

    fun brokerStatus(): Result<String> {
        return try {
            val client = KubeMQClient.pubSub {
                address = startupBrokerAddress
                clientId = "burnin-health"
            }
            val info = kotlinx.coroutines.runBlocking { client.ping() }
            client.close()
            Result.success("connected to ${info.host}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPatterns(broker: String, config: RunConfig): List<BasePattern> {
        val list = mutableListOf<BasePattern>()
        if (config.isPatternEnabled("events")) {
            list.add(EventsPattern(broker, config.effectivePatternConfig("events")))
        }
        if (config.isPatternEnabled("events_store")) {
            list.add(EventsStorePattern(broker, config.effectivePatternConfig("events_store")))
        }
        if (config.isPatternEnabled("queue_stream")) {
            list.add(QueueStreamPattern(broker, config.effectivePatternConfig("queue_stream")))
        }
        if (config.isPatternEnabled("queue_simple")) {
            list.add(QueueSimplePattern(broker, config.effectivePatternConfig("queue_simple")))
        }
        if (config.isPatternEnabled("commands")) {
            list.add(CommandsPattern(broker, config.effectivePatternConfig("commands")))
        }
        if (config.isPatternEnabled("queries")) {
            list.add(QueriesPattern(broker, config.effectivePatternConfig("queries")))
        }
        return list
    }
}
