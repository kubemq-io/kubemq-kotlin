package io.kubemq.sdk.common

internal object Defaults {
    const val MAX_RECEIVE_SIZE = 104_857_600
    const val RECONNECT_INTERVAL_SECONDS = 1
    const val PING_INTERVAL_SECONDS = 10
    const val PING_TIMEOUT_SECONDS = 5
    const val CONNECTION_TIMEOUT_SECONDS = 10
    const val SHUTDOWN_TIMEOUT_SECONDS = 5
    const val INITIAL_BACKOFF_MS = 500L
    const val MAX_BACKOFF_MS = 30_000L
    const val BACKOFF_MULTIPLIER = 2.0
    const val RETRY_MAX_ATTEMPTS = 3
    const val BUFFER_MAX_SIZE = 1000
    const val POST_RECONNECT_STABILIZATION_MS = 3000L
    const val CHANNEL_MGMT_TIMEOUT_MS = 10_000
    const val REQUESTS_CHANNEL = "kubemq.cluster.internal.requests"
    const val FLOW_CONTROL_WINDOW = 16 * 1024 * 1024
}
