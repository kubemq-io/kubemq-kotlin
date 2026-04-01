// Expected output:
// Connected to localhost v2.x.x (uptime: XXs)

package io.kubemq.sdk.examples.connection

import io.kubemq.sdk.client.ClientConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.client.LogLevel
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"

fun main() = runBlocking {
    basicConfiguration()
    keepAliveConfiguration()
    reconnectionConfiguration()
    logLevelConfiguration()
    environmentConfiguration()
    println("Connect examples completed.")
}

private suspend fun basicConfiguration() {
    println("=== Basic Configuration ===\n")
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = "kotlin-conn-basic"
    }
    client.use {
        val info = it.ping()
        println("Connected: host=${info.host}, version=${info.version}")
        println("Uptime: ${info.serverUpTimeSeconds}s\n")
    }
}

private suspend fun keepAliveConfiguration() {
    println("=== Keep-Alive Configuration ===\n")
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = "kotlin-conn-keepalive"
        keepAlive = true
        pingIntervalSeconds = 30
        pingTimeoutSeconds = 10
    }
    client.use {
        val info = it.ping()
        println("Connected with keep-alive:")
        println("  Ping Interval: 30s, Timeout: 10s")
        println("  Server: ${info.host}\n")
    }
}

private suspend fun reconnectionConfiguration() {
    println("=== Reconnection Configuration ===\n")
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = "kotlin-conn-reconnect"
        reconnection {
            initialBackoffMs = 1000
            maxBackoffMs = 30_000
            multiplier = 2.0
            maxRetries = 10
        }
    }
    client.use {
        val info = it.ping()
        println("Connected with reconnection config:")
        println("  Initial backoff: 1000ms, Max: 30s, Multiplier: 2.0")
        println("  Server: ${info.host}\n")
    }
}

private suspend fun logLevelConfiguration() {
    println("=== Log Level Configuration ===\n")
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = "kotlin-conn-debug"
        logLevel = LogLevel.DEBUG
    }
    client.use {
        it.ping()
        println("DEBUG logging client connected.\n")
    }
}

private suspend fun environmentConfiguration() {
    println("=== Environment Configuration ===\n")
    val envConfig = ClientConfig.fromEnvironment()
    println("Env address: ${envConfig.address}")
    println("Env clientId: ${envConfig.clientId}")

    val client = KubeMQClient.pubSub {
        address = envConfig.address.ifBlank { ADDRESS }
        clientId = envConfig.clientId.ifBlank { "kotlin-conn-env" }
        authToken = envConfig.authToken
    }
    client.use {
        println("Environment-based client created.\n")
    }
}
