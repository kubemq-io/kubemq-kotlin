package io.kubemq.sdk.observability

internal object NoOpMetrics : KubeMQMetrics {
    override fun recordMessageSent(pattern: String, channel: String, sizeBytes: Long) {}
    override fun recordMessageReceived(pattern: String, channel: String, sizeBytes: Long) {}
    override fun recordOperationDuration(operation: String, durationMs: Long, success: Boolean) {}
    override fun recordError(operation: String, errorCode: String) {}
    override fun recordReconnection(attempt: Int, success: Boolean) {}
    override fun recordActiveSubscription(pattern: String, delta: Int) {}
}
