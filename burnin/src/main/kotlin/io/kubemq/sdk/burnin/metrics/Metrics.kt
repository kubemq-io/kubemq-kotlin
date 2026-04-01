package io.kubemq.sdk.burnin.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

object Metrics {

    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val sentCounters = ConcurrentHashMap<String, Counter>()
    private val receivedCounters = ConcurrentHashMap<String, Counter>()
    private val errorCounters = ConcurrentHashMap<String, Counter>()
    private val latencyTimers = ConcurrentHashMap<String, Timer>()

    fun sent(pattern: String, channel: String, count: Long = 1) {
        val key = "$pattern:$channel"
        sentCounters.getOrPut(key) {
            Counter.builder("burnin_messages_sent")
                .tag("pattern", pattern)
                .tag("channel", channel)
                .register(registry)
        }.increment(count.toDouble())
    }

    fun received(pattern: String, channel: String, count: Long = 1) {
        val key = "$pattern:$channel"
        receivedCounters.getOrPut(key) {
            Counter.builder("burnin_messages_received")
                .tag("pattern", pattern)
                .tag("channel", channel)
                .register(registry)
        }.increment(count.toDouble())
    }

    fun error(pattern: String, channel: String, count: Long = 1) {
        val key = "$pattern:$channel"
        errorCounters.getOrPut(key) {
            Counter.builder("burnin_messages_errors")
                .tag("pattern", pattern)
                .tag("channel", channel)
                .register(registry)
        }.increment(count.toDouble())
    }

    fun recordLatency(pattern: String, channel: String, durationMs: Long) {
        val key = "$pattern:$channel"
        latencyTimers.getOrPut(key) {
            Timer.builder("burnin_latency")
                .tag("pattern", pattern)
                .tag("channel", channel)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99, 0.999)
                .register(registry)
        }.record(Duration.ofMillis(durationMs))
    }

    fun scrape(): String = registry.scrape()

    fun reset() {
        sentCounters.clear()
        receivedCounters.clear()
        errorCounters.clear()
        latencyTimers.clear()
        registry.clear()
    }
}
