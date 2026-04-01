package io.kubemq.sdk.examples.errorhandling

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-errorhandling-graceful-shutdown"
private const val CHANNEL = "kotlin-errorhandling.shutdown-test"

fun main() = runBlocking {
    println("=== Graceful Shutdown ===\n")

    // Create multiple clients
    val pubSubClient = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = "$CLIENT_ID-pubsub"
    }
    val queuesClient = KubeMQClient.queues {
        address = ADDRESS
        clientId = "$CLIENT_ID-queues"
    }
    val cqClient = KubeMQClient.cq {
        address = ADDRESS
        clientId = "$CLIENT_ID-cq"
    }

    println("Created 3 clients.")

    // Create channel and subscribe
    pubSubClient.createEventsChannel(CHANNEL)
    val subJob: Job = launch {
        pubSubClient.subscribeToEvents {
            channel = CHANNEL
        }.collect { /* process events */ }
    }
    println("Subscription active.\n")

    // Send messages before shutdown
    repeat(3) { i ->
        pubSubClient.publishEvent(eventMessage {
            channel = CHANNEL
            body = "Message ${i + 1}".toByteArray()
        })
    }
    delay(300)

    // Graceful shutdown sequence
    println("--- Initiating Graceful Shutdown ---\n")

    // Step 1: Cancel subscriptions
    subJob.cancel()
    println("1. Subscriptions cancelled.")

    // Step 2: Wait for pending operations
    delay(200)
    println("2. Pending operations completed.")

    // Step 3: Clean up channels
    try { pubSubClient.deleteEventsChannel(CHANNEL) } catch (_: Exception) {}
    println("3. Channels cleaned up.")

    // Step 4: Close clients in reverse order
    cqClient.close()
    queuesClient.close()
    pubSubClient.close()
    println("4. All clients closed.\n")

    println("Graceful shutdown complete.")
}
