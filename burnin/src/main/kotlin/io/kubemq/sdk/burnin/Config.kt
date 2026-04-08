package io.kubemq.sdk.burnin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RunConfig(
    val broker: BrokerOverride? = null,
    val mode: String = "soak",
    val duration: String = "15m",
    @SerialName("run_id") val runId: String = "",
    @SerialName("warmup_duration") val warmupDuration: String = "0s",
    @SerialName("starting_timeout_seconds") val startingTimeoutSeconds: Int = 30,
    val patterns: Map<String, PatternConfig> = emptyMap(),
    val queue: QueueConfig? = null,
    val rpc: RpcConfig? = null,
    val message: MessageConfig? = null,
    val thresholds: GlobalThresholds? = null,
    val shutdown: ShutdownConfig? = null,
)

@Serializable
data class BrokerOverride(
    val address: String = "",
)

@Serializable
data class PatternConfig(
    val enabled: Boolean = true,
    val channels: Int = 1,
    @SerialName("producers_per_channel") val producersPerChannel: Int = 1,
    @SerialName("consumers_per_channel") val consumersPerChannel: Int = 1,
    @SerialName("senders_per_channel") val sendersPerChannel: Int = 1,
    @SerialName("responders_per_channel") val respondersPerChannel: Int = 1,
    val rate: Int = 10,
    val thresholds: PatternThresholds? = null,
)

@Serializable
data class PatternThresholds(
    @SerialName("max_loss_pct") val maxLossPct: Double? = null,
    @SerialName("max_p99_latency_ms") val maxP99LatencyMs: Double? = null,
)

@Serializable
data class QueueConfig(
    @SerialName("poll_max_messages") val pollMaxMessages: Int = 10,
    @SerialName("poll_wait_timeout_seconds") val pollWaitTimeoutSeconds: Int = 5,
    @SerialName("auto_ack") val autoAck: Boolean = true,
)

@Serializable
data class RpcConfig(
    @SerialName("timeout_ms") val timeoutMs: Int = 10_000,
)

@Serializable
data class MessageConfig(
    @SerialName("size_bytes") val sizeBytes: Int = 256,
)

@Serializable
data class GlobalThresholds(
    @SerialName("max_duplication_pct") val maxDuplicationPct: Double = 1.0,
    @SerialName("max_error_rate_pct") val maxErrorRatePct: Double = 1.0,
    @SerialName("max_loss_pct") val maxLossPct: Double = 1.0,
)

@Serializable
data class ShutdownConfig(
    @SerialName("drain_timeout_seconds") val drainTimeoutSeconds: Int = 10,
    @SerialName("cleanup_channels") val cleanupChannels: Boolean = true,
)

@Serializable
data class MetricsConfig(
    val port: Int = 8888,
)

@Serializable
data class StartupConfig(
    val metrics: MetricsConfig = MetricsConfig(),
)

val ALL_PATTERN_NAMES = listOf("events", "events_store", "queue_stream", "queue_simple", "commands", "queries")

fun RunConfig.effectiveBrokerAddress(startupAddress: String): String {
    val override = broker?.address
    return if (!override.isNullOrBlank()) override else startupAddress
}

fun RunConfig.effectivePatternConfig(name: String): PatternConfig {
    return patterns[name] ?: PatternConfig()
}

fun RunConfig.isPatternEnabled(name: String): Boolean {
    val pc = patterns[name]
    return pc?.enabled ?: true
}

fun RunConfig.enabledPatternCount(): Int =
    ALL_PATTERN_NAMES.count { isPatternEnabled(it) }

fun RunConfig.validate(): List<String> {
    val errors = mutableListOf<String>()
    if (mode.isNotBlank() && mode != "soak" && mode != "benchmark") {
        errors.add("mode: must be 'soak' or 'benchmark', got '$mode'")
    }
    if (duration.isNotBlank() && !duration.matches(Regex("^\\d+[smhd]$"))) {
        errors.add("duration: must match \\d+[smhd], got '$duration'")
    }
    if (enabledPatternCount() == 0) {
        errors.add("no patterns enabled")
    }
    for (pname in ALL_PATTERN_NAMES) {
        val pc = patterns[pname] ?: continue
        if (!pc.enabled) continue
        if (pc.rate < 0) errors.add("patterns.$pname.rate: must be >= 0")
        if (pc.channels < 1 || pc.channels > 1000) errors.add("patterns.$pname.channels: must be 1-1000")
    }
    return errors
}

fun parseDuration(s: String): Long {
    if (s.isBlank()) return 0L
    val match = Regex("^(\\d+)([smhd])$").matchEntire(s)
        ?: throw IllegalArgumentException("Invalid duration: $s")
    val value = match.groupValues[1].toLong()
    return when (match.groupValues[2]) {
        "s" -> value * 1000
        "m" -> value * 60 * 1000
        "h" -> value * 3600 * 1000
        "d" -> value * 86400 * 1000
        else -> throw IllegalArgumentException("Invalid duration unit: ${match.groupValues[2]}")
    }
}
