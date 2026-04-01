package io.kubemq.sdk.observability

import io.kubemq.sdk.client.LogLevel

internal object KubeMQLoggerFactory {
    fun create(level: LogLevel): KubeMQLogger {
        return try {
            Class.forName("org.slf4j.LoggerFactory")
            Slf4jKubeMQLogger(level)
        } catch (_: ClassNotFoundException) {
            NoOpLogger
        }
    }
}
