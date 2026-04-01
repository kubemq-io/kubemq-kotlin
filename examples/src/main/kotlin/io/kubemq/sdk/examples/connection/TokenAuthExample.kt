package io.kubemq.sdk.examples.connection

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-connection-token-auth"
private const val AUTH_TOKEN = "your-jwt-token-or-api-key"

fun main() = runBlocking {
    // 1. Connect with environment token
    println("=== Connecting with Token from Environment ===\n")
    val envToken = System.getenv("KUBEMQ_AUTH_TOKEN")
    if (envToken.isNullOrBlank()) {
        println("KUBEMQ_AUTH_TOKEN environment variable not set.")
        println("  export KUBEMQ_AUTH_TOKEN=your-token-here\n")
    } else {
        val envClient = KubeMQClient.queues {
            address = ADDRESS
            clientId = CLIENT_ID
            authToken = envToken
        }
        envClient.use {
            val info = it.ping()
            println("Connected with environment token!")
            println("Server: ${info.host} v${info.version}")
        }
    }

    // 2. Connect with direct token
    println("=== Connecting with Authentication Token ===\n")
    try {
        val client = KubeMQClient.queues {
            address = ADDRESS
            clientId = CLIENT_ID
            authToken = AUTH_TOKEN
        }
        client.use {
            val info = it.ping()
            println("Successfully authenticated and connected!")
            println("Server: ${info.host}")
        }
    } catch (e: Exception) {
        println("Authentication failed: ${e.message}")
    }

    println("\nToken auth examples completed.")
}
