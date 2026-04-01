package io.kubemq.sdk.unit.retry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.JitterType
import io.kubemq.sdk.client.LogLevel
import io.kubemq.sdk.exception.ErrorCode
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.observability.KubeMQLogger
import io.kubemq.sdk.retry.RetryExecutor
import io.kubemq.sdk.retry.RetryPolicy
import java.util.concurrent.atomic.AtomicInteger

class RetryExecutorTest : FunSpec({

    val noOpLogger = object : KubeMQLogger {
        override fun trace(message: () -> String) {}
        override fun debug(message: () -> String) {}
        override fun info(message: () -> String) {}
        override fun warn(message: () -> String) {}
        override fun error(message: () -> String, cause: Throwable?) {}
        override fun isEnabled(level: LogLevel): Boolean = false
    }

    test("succeeds on first attempt") {
        val policy = RetryPolicy(maxAttempts = 3)
        val result = RetryExecutor.execute(policy, noOpLogger, "test") { "ok" }
        result shouldBe "ok"
    }

    test("retries on retryable exception and then succeeds") {
        val attempts = AtomicInteger(0)
        val policy = RetryPolicy(
            maxAttempts = 3,
            initialBackoffMs = 10,
            maxBackoffMs = 100,
        )
        val result = RetryExecutor.execute(policy, noOpLogger, "test") { attempt ->
            attempts.incrementAndGet()
            if (attempt < 3) {
                throw KubeMQException.Connection("fail", operation = "test")
            }
            "success"
        }
        result shouldBe "success"
        attempts.get() shouldBe 3
    }

    test("throws immediately on non-retryable KubeMQException") {
        val attempts = AtomicInteger(0)
        val policy = RetryPolicy(maxAttempts = 5, initialBackoffMs = 10)
        shouldThrow<KubeMQException.Authentication> {
            RetryExecutor.execute(policy, noOpLogger, "test") {
                attempts.incrementAndGet()
                throw KubeMQException.Authentication("bad auth", operation = "test")
            }
        }
        attempts.get() shouldBe 1
    }

    test("throws immediately on non-KubeMQ exception") {
        val attempts = AtomicInteger(0)
        val policy = RetryPolicy(maxAttempts = 5, initialBackoffMs = 10)
        shouldThrow<IllegalStateException> {
            RetryExecutor.execute(policy, noOpLogger, "test") {
                attempts.incrementAndGet()
                throw IllegalStateException("boom")
            }
        }
        attempts.get() shouldBe 1
    }

    test("exhausts all attempts and throws last error") {
        val attempts = AtomicInteger(0)
        val policy = RetryPolicy(maxAttempts = 3, initialBackoffMs = 10, maxBackoffMs = 50)
        val ex = shouldThrow<KubeMQException.Connection> {
            RetryExecutor.execute(policy, noOpLogger, "test") {
                attempts.incrementAndGet()
                throw KubeMQException.Connection("fail", operation = "test")
            }
        }
        attempts.get() shouldBe 3
        ex.message shouldBe "fail"
    }

    test("retryable exception with code not in retryableStatusCodes is not retried") {
        val attempts = AtomicInteger(0)
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialBackoffMs = 10,
            retryableStatusCodes = setOf(ErrorCode.DEADLINE_EXCEEDED), // UNAVAILABLE not included
        )
        shouldThrow<KubeMQException.Connection> {
            RetryExecutor.execute(policy, noOpLogger, "test") {
                attempts.incrementAndGet()
                throw KubeMQException.Connection("fail", operation = "test")
            }
        }
        attempts.get() shouldBe 1
    }

    test("maxAttempts=1 means no retry") {
        val attempts = AtomicInteger(0)
        val policy = RetryPolicy(maxAttempts = 1, initialBackoffMs = 10)
        shouldThrow<KubeMQException.Connection> {
            RetryExecutor.execute(policy, noOpLogger, "test") {
                attempts.incrementAndGet()
                throw KubeMQException.Connection("fail", operation = "test")
            }
        }
        attempts.get() shouldBe 1
    }

    test("maxAttempts < 1 throws IllegalArgumentException") {
        val policy = RetryPolicy(maxAttempts = 0, initialBackoffMs = 10)
        shouldThrow<IllegalArgumentException> {
            RetryExecutor.execute(policy, noOpLogger, "test") { "ok" }
        }
    }

    context("computeBackoff") {

        test("NONE jitter returns exact exponential backoff") {
            val policy = RetryPolicy(
                initialBackoffMs = 100,
                multiplier = 2.0,
                maxBackoffMs = 10_000,
                jitter = JitterType.NONE,
            )
            RetryExecutor.computeBackoff(1, policy) shouldBe 100
            RetryExecutor.computeBackoff(2, policy) shouldBe 200
            RetryExecutor.computeBackoff(3, policy) shouldBe 400
            RetryExecutor.computeBackoff(4, policy) shouldBe 800
        }

        test("NONE jitter caps at maxBackoffMs") {
            val policy = RetryPolicy(
                initialBackoffMs = 100,
                multiplier = 2.0,
                maxBackoffMs = 300,
                jitter = JitterType.NONE,
            )
            RetryExecutor.computeBackoff(1, policy) shouldBe 100
            RetryExecutor.computeBackoff(2, policy) shouldBe 200
            RetryExecutor.computeBackoff(3, policy) shouldBe 300 // capped
            RetryExecutor.computeBackoff(4, policy) shouldBe 300 // capped
        }

        test("FULL jitter returns value in range 0..cappedMs") {
            val policy = RetryPolicy(
                initialBackoffMs = 100,
                multiplier = 2.0,
                maxBackoffMs = 10_000,
                jitter = JitterType.FULL,
            )
            repeat(100) {
                val backoff = RetryExecutor.computeBackoff(1, policy)
                backoff shouldBeGreaterThanOrEqual 0
                backoff shouldBeLessThanOrEqual 100
            }
        }

        test("EQUAL jitter returns value in range half..cappedMs") {
            val policy = RetryPolicy(
                initialBackoffMs = 100,
                multiplier = 2.0,
                maxBackoffMs = 10_000,
                jitter = JitterType.EQUAL,
            )
            repeat(100) {
                val backoff = RetryExecutor.computeBackoff(1, policy)
                backoff shouldBeGreaterThanOrEqual 50
                backoff shouldBeLessThanOrEqual 100
            }
        }
    }
})
