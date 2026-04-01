package io.kubemq.sdk.examples.connection

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-connection-custom-timeouts"

fun main() = runBlocking {
    connectionTimeoutExample()
    keepAliveTimeoutsExample()
    reconnectionTimeoutExample()
    unaryTimeoutExample()
    println("Custom timeouts examples completed.")
}

private suspend fun connectionTimeoutExample() {
    println("=== Connection Timeout Configuration ===\n")

    // ensureConnectedTimeoutMs controls how long to wait for connection
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = "$CLIENT_ID-conn-timeout"
        ensureConnectedTimeoutMs = 10_000 // 10 seconds
    }
    client.use {
        val info = it.ping()
        println("Connected with 10s connection timeout.")
        println("Server: ${info.host}\n")
    }
}

private suspend fun keepAliveTimeoutsExample() {
    println("=== Keep-Alive Timeout Configuration ===\n")

    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = "$CLIENT_ID-keepalive"
        keepAlive = true
        pingIntervalSeconds = 15
        pingTimeoutSeconds = 5
    }
    client.use {
        val info = it.ping()
        println("Connected with custom keep-alive:")
        println("  Ping Interval: 15 seconds")
        println("  Ping Timeout: 5 seconds")
        println("  Server: ${info.host}\n")
    }
}

private suspend fun reconnectionTimeoutExample() {
    println("=== Reconnection Interval Configuration ===\n")

    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = "$CLIENT_ID-reconnect"
        reconnection {
            initialBackoffMs = 2000
            maxBackoffMs = 60_000
            multiplier = 2.0
        }
    }
    client.use {
        val info = it.ping()
        println("Connected with 2s base reconnect interval.")
        println("  Backoff: 2s, 4s, 8s, 16s, ... up to 60s")
        println("  Server: ${info.host}\n")
    }
}

private suspend fun unaryTimeoutExample() {
    println("=== Unary Timeout Configuration ===\n")

    // unaryTimeoutMs controls timeout for unary gRPC calls
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = "$CLIENT_ID-unary"
        unaryTimeoutMs = 30_000 // 30 seconds
    }
    client.use {
        val info = it.ping()
        println("Connected with 30s unary timeout.")
        println("Server: ${info.host}\n")
    }
}
