package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-replay-from-time"
private const val CHANNEL = "kotlin-eventsstore.replay-from-time"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Record the timestamp before publishing
        val beforePublish = Instant.now()

        // Publish 5 events
        repeat(5) { i ->
            val result = client.publishEventStore(eventStoreMessage {
                channel = CHANNEL
                body = "Time event #${i + 1}".toByteArray()
            })
            println("Published #${i + 1}, sent=${result.sent}")
        }

        delay(500)

        // Subscribe from the recorded timestamp (nanos since epoch)
        val timestampNanos = beforePublish.epochSecond * 1_000_000_000L + beforePublish.nano
        println("\nSubscribing from time ${beforePublish}:")
        val subJob = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartAtTime(timestampNanos)
            }.take(5).collect { msg ->
                println("  seq=${msg.sequence}: ${String(msg.body)}")
            }
        }

        subJob.join()
        println("Done.")
    }
}
