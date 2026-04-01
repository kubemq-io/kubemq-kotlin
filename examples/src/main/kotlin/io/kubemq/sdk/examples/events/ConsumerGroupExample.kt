package io.kubemq.sdk.examples.events

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-events-consumer-group"
private const val CHANNEL = "kotlin-events.consumer-group"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        val numSubscribers = 3
        val numMessages = 9
        val counts = Array(numSubscribers) { AtomicInteger(0) }
        val totalReceived = AtomicInteger(0)

        // Subscribe with consumer group -- load balancing across members
        val jobs = (0 until numSubscribers).map { i ->
            launch {
                client.subscribeToEvents {
                    channel = CHANNEL
                    group = "processors"
                }.collect { msg ->
                    counts[i].incrementAndGet()
                    println("[group-${i + 1}] ${msg.channel}: ${String(msg.body)}")
                    totalReceived.incrementAndGet()
                }
            }
        }

        delay(500)

        // Publish messages -- each delivered to exactly one subscriber in the group
        repeat(numMessages) { i ->
            client.publishEvent(eventMessage {
                channel = CHANNEL
                body = "Message #${i + 1}".toByteArray()
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
