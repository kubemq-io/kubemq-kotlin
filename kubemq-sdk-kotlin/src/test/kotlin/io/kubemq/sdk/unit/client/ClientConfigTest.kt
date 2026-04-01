package io.kubemq.sdk.unit.client

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kubemq.sdk.client.BufferOverflowPolicy
import io.kubemq.sdk.client.ClientConfig
import io.kubemq.sdk.client.JitterType
import io.kubemq.sdk.client.LogLevel
import io.kubemq.sdk.common.Defaults

class ClientConfigTest : FunSpec({

    test("default values are correct") {
        val config = ClientConfig()
        config.address shouldBe ""
        config.clientId shouldBe ""
        config.authToken shouldBe ""
        config.tls shouldBe null
        config.maxReceiveSize shouldBe Defaults.MAX_RECEIVE_SIZE
        config.keepAlive shouldBe true
        config.pingIntervalSeconds shouldBe Defaults.PING_INTERVAL_SECONDS
        config.pingTimeoutSeconds shouldBe Defaults.PING_TIMEOUT_SECONDS
        config.logLevel shouldBe LogLevel.INFO
    }

    test("setting basic properties") {
        val config = ClientConfig()
        config.address = "localhost:50000"
        config.clientId = "test-client"
        config.authToken = "my-token"
        config.maxReceiveSize = 1024
        config.keepAlive = false
        config.pingIntervalSeconds = 20
        config.pingTimeoutSeconds = 10
        config.logLevel = LogLevel.DEBUG

        config.address shouldBe "localhost:50000"
        config.clientId shouldBe "test-client"
        config.authToken shouldBe "my-token"
        config.maxReceiveSize shouldBe 1024
        config.keepAlive shouldBe false
        config.pingIntervalSeconds shouldBe 20
        config.pingTimeoutSeconds shouldBe 10
        config.logLevel shouldBe LogLevel.DEBUG
    }

    test("tls DSL builder") {
        val config = ClientConfig()
        config.tls {
            certFile = "/path/to/cert"
            keyFile = "/path/to/key"
            caCertFile = "/path/to/ca"
            insecureSkipVerify = true
        }
        config.tls shouldNotBe null
        config.tls!!.certFile shouldBe "/path/to/cert"
        config.tls!!.keyFile shouldBe "/path/to/key"
        config.tls!!.caCertFile shouldBe "/path/to/ca"
        config.tls!!.insecureSkipVerify shouldBe true
    }

    test("reconnection DSL builder") {
        val config = ClientConfig()
        config.reconnection {
            initialBackoffMs = 1000
            maxBackoffMs = 60_000
            multiplier = 3.0
            maxRetries = 10
            jitter = JitterType.NONE
        }
        config.reconnectionConfig.initialBackoffMs shouldBe 1000
        config.reconnectionConfig.maxBackoffMs shouldBe 60_000
        config.reconnectionConfig.multiplier shouldBe 3.0
        config.reconnectionConfig.maxRetries shouldBe 10
        config.reconnectionConfig.jitter shouldBe JitterType.NONE
    }

    test("reconnection defaults") {
        val config = ClientConfig()
        config.reconnectionConfig.initialBackoffMs shouldBe Defaults.INITIAL_BACKOFF_MS
        config.reconnectionConfig.maxBackoffMs shouldBe Defaults.MAX_BACKOFF_MS
        config.reconnectionConfig.multiplier shouldBe Defaults.BACKOFF_MULTIPLIER
        config.reconnectionConfig.maxRetries shouldBe 0
        config.reconnectionConfig.jitter shouldBe JitterType.FULL
    }

    test("buffer DSL builder") {
        val config = ClientConfig()
        config.buffer {
            enabled = true
            maxSize = 500
            overflowPolicy = BufferOverflowPolicy.DROP_OLDEST
        }
        config.bufferConfig.enabled shouldBe true
        config.bufferConfig.maxSize shouldBe 500
        config.bufferConfig.overflowPolicy shouldBe BufferOverflowPolicy.DROP_OLDEST
    }

    test("buffer defaults") {
        val config = ClientConfig()
        config.bufferConfig.enabled shouldBe false
        config.bufferConfig.maxSize shouldBe Defaults.BUFFER_MAX_SIZE
        config.bufferConfig.overflowPolicy shouldBe BufferOverflowPolicy.REJECT
    }

    test("fromEnvironment returns config with empty defaults when no env set") {
        // This test verifies the factory method runs without error.
        // Env vars may or may not be set in CI, so we just verify it doesn't throw.
        val config = ClientConfig.fromEnvironment()
        config shouldNotBe null
    }

    test("LogLevel enum has all expected values") {
        LogLevel.values().map { it.name } shouldBe listOf(
            "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF",
        )
    }

    test("JitterType enum has all expected values") {
        JitterType.values().map { it.name } shouldBe listOf("NONE", "FULL", "EQUAL")
    }

    test("BufferOverflowPolicy enum has all expected values") {
        BufferOverflowPolicy.values().map { it.name } shouldBe listOf(
            "REJECT", "DROP_OLDEST", "DROP_NEWEST", "BLOCK",
        )
    }
})
