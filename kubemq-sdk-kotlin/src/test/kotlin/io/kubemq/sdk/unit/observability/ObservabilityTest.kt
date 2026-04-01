package io.kubemq.sdk.unit.observability

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kubemq.sdk.client.LogLevel
import io.kubemq.sdk.observability.KubeMQLogger
import io.kubemq.sdk.observability.KubeMQMetrics
import io.kubemq.sdk.observability.KubeMQTracing
import io.kubemq.sdk.observability.NoOpLogger
import io.kubemq.sdk.observability.NoOpMetrics
import io.kubemq.sdk.observability.NoOpTracing
import io.kubemq.sdk.observability.SpanScope

class ObservabilityTest : FunSpec({

    context("NoOpLogger") {

        test("implements KubeMQLogger interface") {
            NoOpLogger.shouldBeInstanceOf<KubeMQLogger>()
        }

        test("all logging methods are callable without error") {
            NoOpLogger.trace { "trace msg" }
            NoOpLogger.debug { "debug msg" }
            NoOpLogger.info { "info msg" }
            NoOpLogger.warn { "warn msg" }
            NoOpLogger.error({ "error msg" }, RuntimeException("cause"))
            NoOpLogger.error({ "error no cause" })
        }

        test("isEnabled returns false for all levels") {
            LogLevel.values().forEach { level ->
                NoOpLogger.isEnabled(level) shouldBe false
            }
        }

        test("message lambdas are not evaluated") {
            var called = false
            NoOpLogger.trace { called = true; "msg" }
            NoOpLogger.debug { called = true; "msg" }
            NoOpLogger.info { called = true; "msg" }
            NoOpLogger.warn { called = true; "msg" }
            NoOpLogger.error({ called = true; "msg" })
            called shouldBe false
        }
    }

    context("NoOpMetrics") {

        test("implements KubeMQMetrics interface") {
            NoOpMetrics.shouldBeInstanceOf<KubeMQMetrics>()
        }

        test("all metric methods are callable without error") {
            NoOpMetrics.recordMessageSent("pubsub", "ch1", 100)
            NoOpMetrics.recordMessageReceived("pubsub", "ch1", 200)
            NoOpMetrics.recordOperationDuration("send", 50, true)
            NoOpMetrics.recordOperationDuration("send", 50, false)
            NoOpMetrics.recordError("send", "UNAVAILABLE")
            NoOpMetrics.recordReconnection(1, true)
            NoOpMetrics.recordReconnection(2, false)
            NoOpMetrics.recordActiveSubscription("events", 1)
            NoOpMetrics.recordActiveSubscription("events", -1)
        }
    }

    context("NoOpTracing") {

        test("implements KubeMQTracing interface") {
            NoOpTracing.shouldBeInstanceOf<KubeMQTracing>()
        }

        test("startSpan returns a SpanScope") {
            val span = NoOpTracing.startSpan("op")
            span.shouldBeInstanceOf<SpanScope>()
        }

        test("startSpan with attributes returns a SpanScope") {
            val span = NoOpTracing.startSpan("op", mapOf("key" to "value"))
            span.shouldBeInstanceOf<SpanScope>()
        }

        test("SpanScope operations are callable without error") {
            val span = NoOpTracing.startSpan("test-op")
            span.setAttribute("key1", "value1")
            span.setAttribute("key2", "value2")
            span.recordError(RuntimeException("test"))
            span.setStatus(true)
            span.setStatus(false)
            span.close()
        }

        test("SpanScope can be used in use block") {
            NoOpTracing.startSpan("op").use { span ->
                span.setAttribute("k", "v")
                span.setStatus(true)
            }
            // no exception means it implements Closeable correctly
        }

        test("multiple spans are independent") {
            val span1 = NoOpTracing.startSpan("op1")
            val span2 = NoOpTracing.startSpan("op2")
            span1.setAttribute("a", "1")
            span2.setAttribute("b", "2")
            span1.setStatus(true)
            span2.setStatus(false)
            span1.close()
            span2.close()
        }
    }
})
