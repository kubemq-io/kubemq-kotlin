package io.kubemq.sdk.observability

import io.kubemq.sdk.client.LogLevel

internal object NoOpLogger : KubeMQLogger {
    override fun trace(message: () -> String) {}
    override fun debug(message: () -> String) {}
    override fun info(message: () -> String) {}
    override fun warn(message: () -> String) {}
    override fun error(message: () -> String, cause: Throwable?) {}
    override fun isEnabled(level: LogLevel): Boolean = false
}
