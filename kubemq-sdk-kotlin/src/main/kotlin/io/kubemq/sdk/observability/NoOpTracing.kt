package io.kubemq.sdk.observability

internal object NoOpTracing : KubeMQTracing {

    override fun startSpan(operationName: String, attributes: Map<String, String>): SpanScope {
        return NoOpSpanScope
    }

    private object NoOpSpanScope : SpanScope {
        override fun setAttribute(key: String, value: String) {}
        override fun recordError(throwable: Throwable) {}
        override fun setStatus(success: Boolean) {}
        override fun close() {}
    }
}
