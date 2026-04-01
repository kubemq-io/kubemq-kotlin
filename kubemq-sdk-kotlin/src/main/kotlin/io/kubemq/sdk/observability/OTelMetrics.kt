package io.kubemq.sdk.observability

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter

internal class OTelMetrics : KubeMQMetrics {

    private val meter: Meter = GlobalOpenTelemetry.get()
        .getMeter("io.kubemq.sdk")

    private val messagesSentCounter = meter
        .counterBuilder("kubemq.messages.sent")
        .setDescription("Total messages sent")
        .build()

    private val messagesSentBytes = meter
        .counterBuilder("kubemq.messages.sent.bytes")
        .setDescription("Total bytes sent")
        .build()

    private val messagesReceivedCounter = meter
        .counterBuilder("kubemq.messages.received")
        .setDescription("Total messages received")
        .build()

    private val messagesReceivedBytes = meter
        .counterBuilder("kubemq.messages.received.bytes")
        .setDescription("Total bytes received")
        .build()

    private val operationDuration = meter
        .histogramBuilder("kubemq.operation.duration")
        .setDescription("Operation duration in milliseconds")
        .ofLongs()
        .build()

    private val errorsCounter = meter
        .counterBuilder("kubemq.errors")
        .setDescription("Total errors")
        .build()

    private val reconnectionsCounter = meter
        .counterBuilder("kubemq.reconnections")
        .setDescription("Total reconnection attempts")
        .build()

    private val activeSubscriptions = meter
        .upDownCounterBuilder("kubemq.subscriptions.active")
        .setDescription("Number of active subscriptions")
        .build()

    override fun recordMessageSent(pattern: String, channel: String, sizeBytes: Long) {
        val attrs = Attributes.of(
            AttributeKey.stringKey("kubemq.pattern"), pattern,
            AttributeKey.stringKey("kubemq.channel"), channel,
        )
        messagesSentCounter.add(1, attrs)
        messagesSentBytes.add(sizeBytes, attrs)
    }

    override fun recordMessageReceived(pattern: String, channel: String, sizeBytes: Long) {
        val attrs = Attributes.of(
            AttributeKey.stringKey("kubemq.pattern"), pattern,
            AttributeKey.stringKey("kubemq.channel"), channel,
        )
        messagesReceivedCounter.add(1, attrs)
        messagesReceivedBytes.add(sizeBytes, attrs)
    }

    override fun recordOperationDuration(operation: String, durationMs: Long, success: Boolean) {
        val attrs = Attributes.of(
            AttributeKey.stringKey("kubemq.operation"), operation,
            AttributeKey.booleanKey("kubemq.success"), success,
        )
        operationDuration.record(durationMs, attrs)
    }

    override fun recordError(operation: String, errorCode: String) {
        val attrs = Attributes.of(
            AttributeKey.stringKey("kubemq.operation"), operation,
            AttributeKey.stringKey("kubemq.error.code"), errorCode,
        )
        errorsCounter.add(1, attrs)
    }

    override fun recordReconnection(attempt: Int, success: Boolean) {
        val attrs = Attributes.of(
            AttributeKey.longKey("kubemq.reconnection.attempt"), attempt.toLong(),
            AttributeKey.booleanKey("kubemq.success"), success,
        )
        reconnectionsCounter.add(1, attrs)
    }

    override fun recordActiveSubscription(pattern: String, delta: Int) {
        val attrs = Attributes.of(
            AttributeKey.stringKey("kubemq.pattern"), pattern,
        )
        activeSubscriptions.add(delta.toLong(), attrs)
    }
}
