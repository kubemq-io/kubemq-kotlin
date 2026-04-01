package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-start-at-time-delta"
private const val CHANNEL = "kotlin-eventsstore.start-at-time-delta"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Publish events
        repeat(5) { i ->
            client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Delta event #${i + 1}".toByteArray()
            })
            println("Published #${i + 1}")
        }

        delay(500)

        // Subscribe with StartAtTimeDelta(60) -- events from the last 60 seconds
        println("\nSubscribing with StartAtTimeDelta(60s):")
        val subJob = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartAtTimeDelta(60)
            }.take(5).collect { msg ->
                println("  seq=${msg.sequence}: ${String(msg.body)}")
            }
        }

        subJob.join()
        println("Done.")
    }
}
