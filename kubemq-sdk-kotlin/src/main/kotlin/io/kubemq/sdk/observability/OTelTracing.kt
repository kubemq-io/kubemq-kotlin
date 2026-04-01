package io.kubemq.sdk.observability

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class OTelTracing : KubeMQTracing {

    private val tracer: Tracer = GlobalOpenTelemetry.get()
        .getTracer("io.kubemq.sdk")

    override fun startSpan(operationName: String, attributes: Map<String, String>): SpanScope {
        val spanBuilder = tracer.spanBuilder(operationName)
        for ((key, value) in attributes) {
            spanBuilder.setAttribute(AttributeKey.stringKey(key), value)
        }
        val span = spanBuilder.startSpan()
        val scope = span.makeCurrent()
        return OTelSpanScope(span, scope)
    }

    private class OTelSpanScope(
        private val span: io.opentelemetry.api.trace.Span,
        private val scope: io.opentelemetry.context.Scope,
    ) : SpanScope {

        override fun setAttribute(key: String, value: String) {
            span.setAttribute(AttributeKey.stringKey(key), value)
        }

        override fun recordError(throwable: Throwable) {
            span.recordException(throwable)
            span.setStatus(StatusCode.ERROR, throwable.message ?: "error")
        }

        override fun setStatus(success: Boolean) {
            span.setStatus(if (success) StatusCode.OK else StatusCode.ERROR)
        }

        override fun close() {
            scope.close()
            span.end()
        }
    }
}
