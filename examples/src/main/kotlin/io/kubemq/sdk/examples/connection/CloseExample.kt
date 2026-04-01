package io.kubemq.sdk.examples.connection

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-connection-close"

fun main() = runBlocking {
    useBlockExample()
    explicitCloseExample()
    multiClientCloseExample()
    println("\nClose examples completed.")
}

private suspend fun useBlockExample() {
    println("=== Kotlin use { } Pattern (try-with-resources) ===\n")

    // Client auto-closes when leaving use block
    KubeMQClient.queues {
        address = ADDRESS
        clientId = "$CLIENT_ID-use"
    }.use { client ->
        val info = client.ping()
        println("Connected: ${info.host}")
        println("Client will be auto-closed when leaving this block.\n")
    }
    println("Client has been automatically closed.\n")
}

private suspend fun explicitCloseExample() {
    println("=== Explicit Close Pattern ===\n")

    var client: io.kubemq.sdk.pubsub.PubSubClient? = null
    try {
        client = KubeMQClient.pubSub {
            address = ADDRESS
            clientId = "$CLIENT_ID-explicit"
        }
        val info = client.ping()
        println("Connected: ${info.host}")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        client?.close()
        println("Client explicitly closed.\n")
    }
}

private suspend fun multiClientCloseExample() {
    println("=== Multi-Client Close ===\n")

    var pubSubClient: io.kubemq.sdk.pubsub.PubSubClient? = null
    var queuesClient: io.kubemq.sdk.queues.QueuesClient? = null
    var cqClient: io.kubemq.sdk.cq.CQClient? = null

    try {
        pubSubClient = KubeMQClient.pubSub {
            address = ADDRESS
            clientId = "$CLIENT_ID-pubsub"
        }
        queuesClient = KubeMQClient.queues {
            address = ADDRESS
            clientId = "$CLIENT_ID-queues"
        }
        cqClient = KubeMQClient.cq {
            address = ADDRESS
            clientId = "$CLIENT_ID-cq"
        }

        println("Three clients created.")
        pubSubClient.ping()
        queuesClient.ping()
        cqClient.ping()
        println("All clients connected.\n")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        // Close in reverse order
        cqClient?.close()
        println("CQClient closed.")
        queuesClient?.close()
        println("QueuesClient closed.")
        pubSubClient?.close()
        println("PubSubClient closed.")
    }
}
