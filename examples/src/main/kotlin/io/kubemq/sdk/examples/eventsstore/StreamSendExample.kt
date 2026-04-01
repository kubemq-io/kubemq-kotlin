package io.kubemq.sdk.examples.eventsstore

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-eventsstore-stream-send"
private const val CHANNEL = "kotlin-eventsstore.stream-send"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Subscribe to collect results
        val subJob = launch {
            client.subscribeToEventsStore {
                channel = CHANNEL
                startPosition = StartPosition.StartNewOnly
            }.take(100).collect { msg ->
                println("Received: seq=${msg.sequence} ${String(msg.body)}")
            }
        }

        delay(500)

        // High-throughput stream of persistent events
        val eventFlow = flow {
            repeat(100) { i ->
                emit(eventStoreMessage {
                    channel = CHANNEL
                    body = "Stream store message #$i".toByteArray()
                    metadata = "batch"
                })
            }
        }

        println("Streaming 100 persistent events...")
        val start = System.currentTimeMillis()
        client.publishEventStoreStream(eventFlow).collect { result ->
            if (!result.sent) {
                println("Failed to send ${result.id}: ${result.error}")
            }
        }
        val elapsed = System.currentTimeMillis() - start
        println("100 events streamed in ${elapsed}ms (${100 * 1000.0 / elapsed} msg/s)")

        delay(2000)
        subJob.cancel()
        println("Done.")
    }
}
