package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-cancel-subscription"
private const val CHANNEL = "kotlin-eventsstore.cancel-subscription"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        val receivedCount = AtomicInteger(0)

        // 1. Start subscription
        println("1. Starting events store subscription...")
        val subJob: Job = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartNewOnly
            }.collect { msg ->
                val count = receivedCount.incrementAndGet()
                println("  [$count] Received: seq=${msg.sequence} ${String(msg.body)}")
            }
        }

        delay(300)

        // 2. Send messages while subscribed
        println("2. Sending persistent events while subscribed...")
        repeat(3) { i ->
            client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Message ${i + 1}".toByteArray()
            })
            delay(200)
        }

        delay(500)

        // 3. Cancel subscription
        println("\n3. Cancelling subscription...")
        subJob.cancel()
        println("   Subscription cancelled.")

        // 4. Send more messages (should not be received)
        println("4. Sending more messages after cancel...")
        repeat(3) { i ->
            client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Post-cancel message ${i + 1}".toByteArray()
            })
        }

        delay(500)
        println("5. Received ${receivedCount.get()} messages (before cancel).")
        println("\nCancel subscription example completed.")
    }
}
