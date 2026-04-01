package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-replay-from-sequence"
private const val CHANNEL = "kotlin-eventsstore.replay-from-sequence"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Publish 10 events
        repeat(10) { i ->
            val result = client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Event #${i + 1}".toByteArray()
            })
            println("Published #${i + 1}, sent=${result.sent}")
        }

        delay(500)

        // Subscribe from sequence 5 -- should receive events 5-10
        println("\nSubscribing from sequence 5:")
        val subJob = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartAtSequence(5)
            }.take(6).collect { msg ->
                println("  seq=${msg.sequence}: ${String(msg.body)}")
            }
        }

        subJob.join()
        println("Done.")
    }
}
