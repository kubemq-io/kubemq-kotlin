package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-start-from-last"
private const val CHANNEL = "kotlin-eventsstore.start-from-last"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Publish 5 events
        repeat(5) { i ->
            client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Event #${i + 1}".toByteArray()
            })
            println("Published #${i + 1}")
        }

        delay(500)

        // Subscribe with StartFromLast -- receives the last event + any new ones
        println("\nSubscribing with StartFromLast:")
        val subJob = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartFromLast
            }.take(2).collect { msg ->
                println("  seq=${msg.sequence}: ${String(msg.body)}")
            }
        }

        delay(500)

        // Publish a new event after subscription
        client.publishEventStore(eventStoreMessage {
            channel = CHANNEL
            body = "New event after subscription".toByteArray()
        })

        subJob.join()
        println("Done.")
    }
}
