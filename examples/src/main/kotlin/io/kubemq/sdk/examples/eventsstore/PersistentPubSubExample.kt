// Expected output:
// Publishing event store message...
// Sent: id=<uuid>, sent=true
// Subscribing to events store from first...
// Received: sequence=1, channel=events-store.persistent-example

package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-persistent-pubsub"
private const val CHANNEL = "kotlin-eventsstore.persistent-pubsub"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Subscribe with StartNewOnly
        val subJob = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartNewOnly
            }.take(3).collect { msg ->
                println("Received: seq=${msg.sequence} ${String(msg.body)}")
            }
        }

        delay(500)

        // Publish persistent events
        repeat(3) { i ->
            val result = client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Persistent event #$i".toByteArray()
                metadata = "persistence-demo"
            })
            println("Published #$i, sent=${result.sent}")
        }

        subJob.join()
        println("Done.")
    }
}
