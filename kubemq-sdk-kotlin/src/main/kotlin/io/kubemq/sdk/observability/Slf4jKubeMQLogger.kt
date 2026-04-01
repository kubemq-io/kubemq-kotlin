package io.kubemq.sdk.observability

import io.kubemq.sdk.client.LogLevel
import org.slf4j.LoggerFactory

internal class Slf4jKubeMQLogger(
    private val minLevel: LogLevel,
) : KubeMQLogger {

    private val delegate = LoggerFactory.getLogger("io.kubemq.sdk")

    override fun trace(message: () -> String) {
        if (isEnabled(LogLevel.TRACE) && delegate.isTraceEnabled) {
            delegate.trace(message())
        }
    }

    override fun debug(message: () -> String) {
        if (isEnabled(LogLevel.DEBUG) && delegate.isDebugEnabled) {
            delegate.debug(message())
        }
    }

    override fun info(message: () -> String) {
        if (isEnabled(LogLevel.INFO) && delegate.isInfoEnabled) {
            delegate.info(message())
        }
    }

    override fun warn(message: () -> String) {
        if (isEnabled(LogLevel.WARN) && delegate.isWarnEnabled) {
            delegate.warn(message())
        }
    }

    override fun error(message: () -> String, cause: Throwable?) {
        if (isEnabled(LogLevel.ERROR) && delegate.isErrorEnabled) {
            if (cause != null) {
                delegate.error(message(), cause)
            } else {
                delegate.error(message())
            }
        }
    }

    override fun isEnabled(level: LogLevel): Boolean {
        if (minLevel == LogLevel.OFF) return false
        return level.ordinal >= minLevel.ordinal
    }
}
