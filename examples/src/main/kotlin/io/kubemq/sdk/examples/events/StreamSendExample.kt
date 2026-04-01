package io.kubemq.sdk.examples.events

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = "localhost:50000"
        clientId = "stream-example"
    }

    client.use {
        // Subscribe to collect results
        val subJob = launch {
            client.subscribeToEvents {
                channel = "stream.example"
            }.take(100).collect { msg ->
                println("Received: ${String(msg.body)}")
            }
        }

        delay(500)

        // High-throughput stream of events
        val eventFlow = flow {
            repeat(100) { i ->
                emit(eventMessage {
                    channel = "stream.example"
                    body = "Stream message #$i".toByteArray()
                    metadata = "batch"
                })
            }
        }

        // publishEventStream returns a Flow of send results
        client.publishEventStream(eventFlow).collect { result ->
            if (!result.sent) {
                println("Failed to send ${result.id}: ${result.error}")
            }
        }

        println("All 100 events streamed.")
        delay(1000)
        subJob.cancel()
    }
}
