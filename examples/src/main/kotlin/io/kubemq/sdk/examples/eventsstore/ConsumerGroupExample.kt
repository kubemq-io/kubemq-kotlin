package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-consumer-group"
private const val CHANNEL = "kotlin-eventsstore.consumer-group"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        val numSubscribers = 2
        val numMessages = 6
        val counts = Array(numSubscribers) { AtomicInteger(0) }
        val totalReceived = AtomicInteger(0)

        // Subscribe with consumer group
        val jobs = (0 until numSubscribers).map { i ->
            launch {
                client.subscribeToEventsStore {
                    channel = CHANNEL
                    group = "store-processors"
                    startPosition = StartPosition.StartNewOnly
                }.collect { msg ->
                    counts[i].incrementAndGet()
                    println("[group-${i + 1}] seq=${msg.sequence}: ${String(msg.body)}")
                    totalReceived.incrementAndGet()
                }
            }
        }

        delay(500)

        // Publish persistent events
        repeat(numMessages) { i ->
            client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Store event #${i + 1}".toByteArray()
            })
            delay(100)
        }

        delay(2000)

        println("\nDistribution:")
        for (i in 0 until numSubscribers) {
            println("  Subscriber ${i + 1}: ${counts[i].get()} messages")
        }

        jobs.forEach { it.cancel() }
        println("Done.")
    }
}
