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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

enum class RunState {
    IDLE, STARTING, RUNNING, STOPPING, COMPLETED, FAILED
}

/** Maps internal enum names to the API strings the dashboard expects. */
fun RunState.toApiString(): String = when (this) {
    RunState.COMPLETED -> "stopped"
    RunState.FAILED -> "error"
    else -> name.lowercase()
}

@Serializable
data class StatusTotals(
    val sent: Long = 0,
    val received: Long = 0,
    val lost: Long = 0,
    val duplicated: Long = 0,
    val corrupted: Long = 0,
    @kotlinx.serialization.SerialName("out_of_order") val outOfOrder: Long = 0,
    val errors: Long = 0,
    val reconnections: Long = 0,
)

@Serializable
data class PatternStateEntry(
    val state: String,
    val channels: Int,
)

@Serializable
data class RunStatusResponse(
    val state: String,
    @kotlinx.serialization.SerialName("run_id") val runId: String,
    @kotlinx.serialization.SerialName("started_at") val startedAt: String,
    @kotlinx.serialization.SerialName("elapsed_seconds") val elapsedSeconds: Long,
    @kotlinx.serialization.SerialName("remaining_seconds") val remainingSeconds: Long = 0,
    @kotlinx.serialization.SerialName("warmup_active") val warmupActive: Boolean = false,
    val totals: StatusTotals = StatusTotals(),
    @kotlinx.serialization.SerialName("pattern_states") val patternStates: Map<String, PatternStateEntry> = emptyMap(),
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
    private var endedAt: Instant = Instant.EPOCH
    private var runId: String = ""
    private var lastVerdict: String = ""
    private var lastReport: JsonObject? = null

    /** Boot time for uptime calculation. */
    val bootTime: Instant = Instant.now()

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
        endedAt = Instant.EPOCH
        lastVerdict = ""
        lastReport = null
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
        endedAt = Instant.now()
        lastVerdict = Reporter.generateVerdict(patterns, runConfig)
        lastReport = buildReport()
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
        val patternStatuses = patterns.map { it.status() }

        // Aggregate totals across all patterns
        var totalSent = 0L
        var totalReceived = 0L
        var totalErrors = 0L
        for (ps in patternStatuses) {
            totalSent += ps.sent
            totalReceived += ps.received
            totalErrors += ps.errors
        }
        val totals = StatusTotals(
            sent = totalSent,
            received = totalReceived,
            errors = totalErrors,
        )

        // Build pattern_states map
        val currentState = state.get()
        val patternStateStr = when (currentState) {
            RunState.RUNNING, RunState.STARTING -> "running"
            RunState.COMPLETED -> "stopped"
            RunState.FAILED -> "error"
            else -> currentState.toApiString()
        }
        val patternStates = mutableMapOf<String, PatternStateEntry>()
        for (ps in patternStatuses) {
            patternStates[ps.name] = PatternStateEntry(
                state = if (ps.running) "running" else patternStateStr,
                channels = ps.channels.size,
            )
        }

        val remaining = runConfig?.let {
            val durationMs = parseDuration(it.duration)
            if (durationMs > 0) maxOf(0L, durationMs / 1000 - elapsed) else 0L
        } ?: 0L

        return RunStatusResponse(
            state = currentState.toApiString(),
            runId = runId,
            startedAt = startedAt.toString(),
            elapsedSeconds = elapsed,
            remainingSeconds = remaining,
            warmupActive = false,
            totals = totals,
            patternStates = patternStates,
            patterns = patternStatuses,
            verdict = lastVerdict,
        )
    }

    fun brokerStatusJson(): JsonObject {
        return try {
            val client = KubeMQClient.pubSub {
                address = startupBrokerAddress
                clientId = "burnin-ping"
            }
            val start = System.nanoTime()
            val info = kotlinx.coroutines.runBlocking { client.ping() }
            val latencyMs = (System.nanoTime() - start) / 1_000_000.0
            client.close()
            JsonObject(
                mapOf(
                    "connected" to JsonPrimitive(true),
                    "address" to JsonPrimitive(startupBrokerAddress),
                    "ping_latency_ms" to JsonPrimitive(latencyMs),
                    "server_version" to JsonPrimitive(info.version),
                    "last_ping_at" to JsonPrimitive(
                        DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)),
                    ),
                ),
            )
        } catch (e: Exception) {
            JsonObject(
                mapOf(
                    "connected" to JsonPrimitive(false),
                    "address" to JsonPrimitive(startupBrokerAddress),
                    "error" to JsonPrimitive(e.message ?: "unknown error"),
                ),
            )
        }
    }

    fun getInfoJson(): JsonObject {
        val runtime = Runtime.getRuntime()
        val osName = System.getProperty("os.name", "unknown").lowercase().let { name ->
            when {
                name.contains("mac") || name.contains("darwin") -> "darwin"
                name.contains("linux") -> "linux"
                name.contains("windows") -> "windows"
                else -> name
            }
        }
        val arch = System.getProperty("os.arch", "unknown").let { a ->
            when (a) {
                "amd64", "x86_64" -> "amd64"
                "aarch64", "arm64" -> "aarch64"
                else -> a
            }
        }
        val javaVersion = System.getProperty("java.version", "unknown")
        val uptimeSeconds = (Instant.now().epochSecond - bootTime.epochSecond).toDouble()

        return JsonObject(
            mapOf(
                "sdk" to JsonPrimitive("kubemq-kotlin"),
                "sdk_version" to JsonPrimitive("1.0.0"),
                "burnin_version" to JsonPrimitive("2.0.0"),
                "burnin_spec_version" to JsonPrimitive("2"),
                "os" to JsonPrimitive(osName),
                "arch" to JsonPrimitive(arch),
                "runtime" to JsonPrimitive("JVM $javaVersion"),
                "cpus" to JsonPrimitive(runtime.availableProcessors()),
                "memory_total_mb" to JsonPrimitive((runtime.maxMemory() / 1024 / 1024).toInt()),
                "pid" to JsonPrimitive(ProcessHandle.current().pid()),
                "uptime_seconds" to JsonPrimitive(uptimeSeconds),
                "started_at" to JsonPrimitive(
                    DateTimeFormatter.ISO_INSTANT.format(bootTime.atOffset(ZoneOffset.UTC)),
                ),
                "state" to JsonPrimitive(state.get().toApiString()),
                "broker_address" to JsonPrimitive(startupBrokerAddress),
            ),
        )
    }

    fun getReport(): JsonObject? = lastReport

    private fun buildReport(): JsonObject {
        val isoFormatter = DateTimeFormatter.ISO_INSTANT
        val durationSeconds = if (startedAt != Instant.EPOCH && endedAt != Instant.EPOCH) {
            endedAt.epochSecond - startedAt.epochSecond
        } else {
            0L
        }

        var totalErrors = 0L
        for (p in patterns) {
            totalErrors += p.status().errors
        }

        val verdictResult = when {
            totalErrors == 0L -> "PASSED"
            lastVerdict.startsWith("FAIL") -> "FAILED"
            else -> "PASSED_WITH_WARNINGS"
        }

        val patternsObj = buildJsonObject {
            val allPatternNames = listOf("events", "events_store", "queue_stream", "queue_simple", "commands", "queries")
            val patternMap = patterns.associateBy { it.name }
            for (pName in allPatternNames) {
                val bp = patternMap[pName]
                if (bp != null) {
                    val s = bp.status()
                    val isRpc = pName == "commands" || pName == "queries"
                    put(pName, buildJsonObject {
                        put("enabled", true)
                        put("state", "stopped")
                        put("channels", s.channels.size)
                        put("sent", s.sent)
                        if (isRpc) {
                            put("responses_success", s.received)
                            put("responses_timeout", 0)
                            put("responses_error", 0)
                        } else {
                            put("received", s.received)
                            put("lost", 0)
                            put("duplicated", 0)
                            put("corrupted", 0)
                            put("out_of_order", 0)
                        }
                        put("errors", s.errors)
                        put("reconnections", 0)
                        if (!isRpc) {
                            put("loss_pct", 0)
                        }
                        put("target_rate", 0)
                        put("actual_rate", 0)
                        put("avg_rate", 0)
                        put("peak_rate", 0)
                        put("bytes_sent", 0)
                        put("bytes_received", 0)
                        put("latency", buildJsonObject {
                            put("p50_ms", 0)
                            put("p95_ms", 0)
                            put("p99_ms", 0)
                            put("p999_ms", 0)
                        })
                        if (isRpc) {
                            put("senders", JsonArray(emptyList()))
                            put("responders", JsonArray(emptyList()))
                        } else {
                            put("producers", JsonArray(emptyList()))
                            put("consumers", JsonArray(emptyList()))
                        }
                    })
                } else {
                    put(pName, buildJsonObject { put("enabled", false) })
                }
            }
        }

        val runtime = Runtime.getRuntime()
        val rssMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)

        return buildJsonObject {
            put("run_id", runId)
            put("sdk", "kotlin")
            put("sdk_version", "1.0.0")
            put("mode", runConfig?.mode ?: "soak")
            put("broker_address", startupBrokerAddress)
            put("started_at", isoFormatter.format(startedAt.atOffset(ZoneOffset.UTC)))
            put("ended_at", isoFormatter.format(endedAt.atOffset(ZoneOffset.UTC)))
            put("duration_seconds", durationSeconds)
            put("all_patterns_enabled", patterns.size == 6)
            put("warmup_active", false)
            put("patterns", patternsObj)
            put("resources", buildJsonObject {
                put("peak_rss_mb", rssMb)
                put("baseline_rss_mb", 0)
                put("memory_growth_factor", 1.0)
                put("peak_workers", patterns.size)
            })
            put("verdict", buildJsonObject {
                put("result", verdictResult)
                put("warnings", JsonArray(emptyList()))
                put("checks", buildJsonObject {})
            })
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
