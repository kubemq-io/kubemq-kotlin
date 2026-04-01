package io.kubemq.sdk.unit.exception

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kubemq.sdk.exception.ErrorCategory
import io.kubemq.sdk.exception.ErrorCode
import io.kubemq.sdk.exception.KubeMQException
import kotlin.time.Duration.Companion.seconds

class KubeMQExceptionTest : FunSpec({

    test("Connection - has correct defaults") {
        val ex = KubeMQException.Connection("server down", operation = "connect", serverAddress = "localhost:50000")
        ex.message shouldBe "server down"
        ex.code shouldBe ErrorCode.UNAVAILABLE
        ex.category shouldBe ErrorCategory.TRANSIENT
        ex.isRetryable shouldBe true
        ex.operation shouldBe "connect"
        ex.serverAddress shouldBe "localhost:50000"
        ex.channel shouldBe null
        ex.requestId shouldBe null
        ex.cause shouldBe null
        ex.shouldBeInstanceOf<RuntimeException>()
    }

    test("Connection - with cause") {
        val cause = RuntimeException("network error")
        val ex = KubeMQException.Connection("server down", cause = cause)
        ex.cause shouldBe cause
    }

    test("Authentication - has correct defaults") {
        val ex = KubeMQException.Authentication("bad token", operation = "auth", serverAddress = "localhost:50000")
        ex.message shouldBe "bad token"
        ex.code shouldBe ErrorCode.UNAUTHENTICATED
        ex.category shouldBe ErrorCategory.PERMANENT
        ex.isRetryable shouldBe false
        ex.operation shouldBe "auth"
        ex.serverAddress shouldBe "localhost:50000"
    }

    test("Authorization - has correct defaults") {
        val ex = KubeMQException.Authorization("denied", operation = "send", channel = "my-channel")
        ex.message shouldBe "denied"
        ex.code shouldBe ErrorCode.PERMISSION_DENIED
        ex.category shouldBe ErrorCategory.PERMANENT
        ex.isRetryable shouldBe false
        ex.operation shouldBe "send"
        ex.channel shouldBe "my-channel"
    }

    test("Timeout - has correct defaults") {
        val dur = 5.seconds
        val ex = KubeMQException.Timeout(
            "timed out", duration = dur,
            operation = "query", channel = "ch1", requestId = "req-1",
        )
        ex.message shouldBe "timed out"
        ex.code shouldBe ErrorCode.DEADLINE_EXCEEDED
        ex.category shouldBe ErrorCategory.TRANSIENT
        ex.isRetryable shouldBe true
        ex.duration shouldBe dur
        ex.operation shouldBe "query"
        ex.channel shouldBe "ch1"
        ex.requestId shouldBe "req-1"
    }

    test("Validation - has correct defaults") {
        val ex = KubeMQException.Validation("bad input", operation = "send", channel = "ch")
        ex.message shouldBe "bad input"
        ex.code shouldBe ErrorCode.INVALID_ARGUMENT
        ex.category shouldBe ErrorCategory.PERMANENT
        ex.isRetryable shouldBe false
        ex.operation shouldBe "send"
        ex.channel shouldBe "ch"
        ex.cause shouldBe null
    }

    test("Validation - with custom error code") {
        val ex = KubeMQException.Validation("already exists", code = ErrorCode.ALREADY_EXISTS)
        ex.code shouldBe ErrorCode.ALREADY_EXISTS
    }

    test("Throttling - has correct defaults") {
        val ex = KubeMQException.Throttling("too many requests", operation = "send", channel = "ch1")
        ex.message shouldBe "too many requests"
        ex.code shouldBe ErrorCode.RESOURCE_EXHAUSTED
        ex.category shouldBe ErrorCategory.TRANSIENT
        ex.isRetryable shouldBe true
        ex.operation shouldBe "send"
        ex.channel shouldBe "ch1"
    }

    test("Transport - has correct defaults") {
        val ex = KubeMQException.Transport("internal error", operation = "send")
        ex.message shouldBe "internal error"
        ex.code shouldBe ErrorCode.INTERNAL
        ex.category shouldBe ErrorCategory.FATAL
        ex.isRetryable shouldBe false
        ex.operation shouldBe "send"
    }

    test("Transport - with custom error code") {
        val ex = KubeMQException.Transport("data loss", code = ErrorCode.DATA_LOSS)
        ex.code shouldBe ErrorCode.DATA_LOSS
    }

    test("Server - has correct defaults") {
        val ex = KubeMQException.Server("unknown error", operation = "send", channel = "ch")
        ex.message shouldBe "unknown error"
        ex.code shouldBe ErrorCode.UNKNOWN
        ex.category shouldBe ErrorCategory.FATAL
        ex.isRetryable shouldBe false
        ex.statusCode shouldBe null
        ex.operation shouldBe "send"
        ex.channel shouldBe "ch"
    }

    test("Server - with all custom parameters") {
        val ex = KubeMQException.Server(
            "aborted", statusCode = 10, code = ErrorCode.ABORTED,
            category = ErrorCategory.TRANSIENT, isRetryable = true,
            operation = "send", channel = "ch",
        )
        ex.statusCode shouldBe 10
        ex.code shouldBe ErrorCode.ABORTED
        ex.category shouldBe ErrorCategory.TRANSIENT
        ex.isRetryable shouldBe true
    }

    test("ClientClosed - has correct defaults") {
        val ex = KubeMQException.ClientClosed()
        ex.message shouldBe "Client is closed"
        ex.code shouldBe ErrorCode.CANCELLED
        ex.category shouldBe ErrorCategory.FATAL
        ex.isRetryable shouldBe false
    }

    test("ClientClosed - with custom message") {
        val ex = KubeMQException.ClientClosed("custom close message")
        ex.message shouldBe "custom close message"
    }

    test("StreamBroken - has correct defaults") {
        val ex = KubeMQException.StreamBroken("stream reset", operation = "subscribe", channel = "events")
        ex.message shouldBe "stream reset"
        ex.code shouldBe ErrorCode.UNAVAILABLE
        ex.category shouldBe ErrorCategory.TRANSIENT
        ex.isRetryable shouldBe true
        ex.operation shouldBe "subscribe"
        ex.channel shouldBe "events"
    }

    test("All subclasses are instances of KubeMQException") {
        val exceptions = listOf(
            KubeMQException.Connection("a"),
            KubeMQException.Authentication("b"),
            KubeMQException.Authorization("c"),
            KubeMQException.Timeout("d"),
            KubeMQException.Validation("e"),
            KubeMQException.Throttling("f"),
            KubeMQException.Transport("g"),
            KubeMQException.Server("h"),
            KubeMQException.ClientClosed(),
            KubeMQException.StreamBroken("j"),
        )
        exceptions.forEach { it.shouldBeInstanceOf<KubeMQException>() }
    }

    test("Retryable exceptions are correctly identified") {
        val retryable = listOf(
            KubeMQException.Connection("a"),
            KubeMQException.Timeout("b"),
            KubeMQException.Throttling("c"),
            KubeMQException.StreamBroken("d"),
        )
        val nonRetryable = listOf(
            KubeMQException.Authentication("a"),
            KubeMQException.Authorization("b"),
            KubeMQException.Validation("c"),
            KubeMQException.Transport("d"),
            KubeMQException.ClientClosed(),
        )

        retryable.forEach { it.isRetryable shouldBe true }
        nonRetryable.forEach { it.isRetryable shouldBe false }
    }

    test("Categories are correctly assigned") {
        KubeMQException.Connection("a").category shouldBe ErrorCategory.TRANSIENT
        KubeMQException.Authentication("b").category shouldBe ErrorCategory.PERMANENT
        KubeMQException.Authorization("c").category shouldBe ErrorCategory.PERMANENT
        KubeMQException.Timeout("d").category shouldBe ErrorCategory.TRANSIENT
        KubeMQException.Validation("e").category shouldBe ErrorCategory.PERMANENT
        KubeMQException.Throttling("f").category shouldBe ErrorCategory.TRANSIENT
        KubeMQException.Transport("g").category shouldBe ErrorCategory.FATAL
        KubeMQException.Server("h").category shouldBe ErrorCategory.FATAL
        KubeMQException.ClientClosed().category shouldBe ErrorCategory.FATAL
        KubeMQException.StreamBroken("j").category shouldBe ErrorCategory.TRANSIENT
    }
})
