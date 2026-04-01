package io.kubemq.sdk.observability

/**
 * Tracing interface for distributed tracing of KubeMQ operations.
 *
 * The SDK provides an OpenTelemetry-based implementation and a no-op default.
 */
public interface KubeMQTracing {
    /**
     * Starts a new trace span for a KubeMQ operation.
     *
     * @param operationName The name of the operation (e.g., "kubemq.sendCommand")
     * @param attributes Optional key-value attributes to attach to the span
     * @return A [SpanScope] that must be closed when the operation completes
     */
    public fun startSpan(operationName: String, attributes: Map<String, String> = emptyMap()): SpanScope
}
