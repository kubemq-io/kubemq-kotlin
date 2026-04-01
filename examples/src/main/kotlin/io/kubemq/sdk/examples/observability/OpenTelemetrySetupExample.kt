package io.kubemq.sdk.examples.observability

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.client.LogLevel
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-observability-otel-setup"
private const val CHANNEL = "kotlin-observability.otel-setup"

/**
 * OpenTelemetry Setup Example
 *
 * The KubeMQ Kotlin SDK automatically integrates with OpenTelemetry.
 * When the OTel Java agent or SDK is present on the classpath, the SDK will:
 *   1. Create spans for message send/receive operations
 *   2. Record metrics: message counts, latencies, errors
 *   3. Propagate trace context across messages
 *   4. Add structured log context
 *
 * To enable OTel with the Java agent:
 *   java -javaagent:opentelemetry-javaagent.jar \
 *        -Dotel.service.name=my-kubemq-service \
 *        -Dotel.traces.exporter=jaeger \
 *        -jar myapp.jar
 */
fun main() = runBlocking {
    println("=== OpenTelemetry Setup Example ===\n")

    println("The KubeMQ Kotlin SDK automatically integrates with OpenTelemetry.")
    println("When the OTel agent or SDK is present, the SDK will:")
    println("  1. Create spans for message send/receive operations")
    println("  2. Record metrics: message counts, latencies, errors")
    println("  3. Propagate trace context across messages")
    println("  4. Add structured log context\n")

    // Create a client (OTel auto-detected when agent/SDK present)
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
        logLevel = LogLevel.INFO
    }

    client.use {
        try {
            val info = client.ping()
            println("Connected to: ${info.host} v${info.version}")

            client.createEventsStoreChannel(CHANNEL)

            // Send messages (generates send spans and metrics)
            println("\nSending messages (generates OTel spans)...")
            repeat(3) { i ->
                val result = client.publishEventStore(eventStoreMessage {
                    channel = CHANNEL
                    body = "Traced message #${i + 1}".toByteArray()
                    metadata = "otel-example"
                })
                println("  Sent #${i + 1} (sent=${result.sent})")
            }

            // Subscribe and receive (generates receive spans)
            println("\nSubscribing (generates OTel receive spans)...")
            val subJob = launch {
                client.subscribeToEventsStore {
                    channel = CHANNEL
                    startPosition = StartPosition.StartFromFirst
                }.take(3).collect { msg ->
                    println("  Received: ${String(msg.body)}")
                }
            }

            subJob.join()
        } finally {
            try { client.deleteEventsStoreChannel(CHANNEL) } catch (_: Exception) {}
        }
    }

    println("\nOTel integration notes:")
    println("  - Spans appear in your trace backend (Jaeger, Zipkin, etc.)")
    println("  - Metrics available via OTel metrics exporter")
    println("  - Log correlation via trace_id and span_id")
    println("  - No code changes needed -- auto-detection works")
    println("\nOpenTelemetry setup example completed.")
}
