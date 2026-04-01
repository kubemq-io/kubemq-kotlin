package io.kubemq.sdk.examples.connection

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-connection-ping"

fun main() = runBlocking {
    println("=== Ping KubeMQ Server ===\n")

    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }
    client.use {
        val serverInfo = client.ping()
        println("Ping successful!")
        println("  Host: ${serverInfo.host}")
        println("  Version: ${serverInfo.version}")
        println("  Server Start Time: ${serverInfo.serverStartTime}")
        println("  Server Uptime: ${serverInfo.serverUpTimeSeconds} seconds")
    }

    // Kotlin SDK does not have validateOnBuild; use explicit ping() instead
    println("\n=== Validate via Explicit Ping ===\n")
    val client2 = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = "$CLIENT_ID-validate"
    }
    client2.use {
        try {
            client2.ping()
            println("Connectivity validated via explicit ping().")
        } catch (e: Exception) {
            println("Connectivity validation failed: ${e.message}")
        }
    }

    println("\nPing examples completed.")
}
