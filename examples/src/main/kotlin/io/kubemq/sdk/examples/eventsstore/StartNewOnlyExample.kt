package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-start-new-only"
private const val CHANNEL = "kotlin-eventsstore.start-new-only"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Publish events BEFORE subscription
        repeat(3) { i ->
            client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Old event #${i + 1}".toByteArray()
            })
            println("Published old event #${i + 1} (before subscription)")
        }

        delay(500)

        // Subscribe with StartNewOnly -- should NOT receive old events
        println("\nSubscribing with StartNewOnly (old events should not appear):")
        val subJob = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartNewOnly
            }.take(2).collect { msg ->
                println("  seq=${msg.sequence}: ${String(msg.body)}")
            }
        }

        delay(500)

        // Publish NEW events after subscription
        repeat(2) { i ->
            client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "New event #${i + 1}".toByteArray()
            })
            println("Published new event #${i + 1} (after subscription)")
        }

        subJob.join()
        println("Done.")
    }
}
