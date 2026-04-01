// Expected output:
// Subscribing to events on channel: events.basic-example
// Publishing event...
// Received event: id=<uuid>, channel=events.basic-example, metadata=hello

package io.kubemq.sdk.examples.events

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-events-basic-pubsub"
private const val CHANNEL = "kotlin-events.basic-pubsub"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Subscribe in background
        val job = launch {
            client.subscribeToEvents {
                channel = CHANNEL
            }.take(3).collect { msg ->
                println("Received: ${String(msg.body)} on ${msg.channel}")
            }
        }

        delay(500) // let subscription establish

        // Publish 3 events
        repeat(3) { i ->
            client.publishEvent(eventMessage {
                channel = CHANNEL
                body = "Hello #$i".toByteArray()
                metadata = "greeting"
            })
            println("Published event #$i")
        }

        job.join()
        println("Done.")
    }
}
