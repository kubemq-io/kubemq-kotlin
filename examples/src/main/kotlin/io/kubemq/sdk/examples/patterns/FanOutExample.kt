package io.kubemq.sdk.examples.patterns

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-patterns-fan-out"
private const val CHANNEL = "kotlin-patterns.fan-out"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        val numSubscribers = 3
        val numMessages = 3
        val counts = Array(numSubscribers) { AtomicInteger(0) }
        val totalReceived = AtomicInteger(0)
        val expectedTotal = numSubscribers * numMessages

        // Subscribe multiple handlers (each receives all messages -- no group)
        val jobs = (0 until numSubscribers).map { i ->
            launch {
                client.subscribeToEvents {
                    channel = CHANNEL
                }.collect { msg ->
                    counts[i].incrementAndGet()
                    println("  Subscriber ${i + 1}: ${String(msg.body)}")
                    totalReceived.incrementAndGet()
                }
            }
        }

        delay(500)

        // Publish messages (fan-out to all subscribers)
        println("Publishing $numMessages messages to $numSubscribers subscribers...\n")
        repeat(numMessages) { i ->
            client.publishEvent(eventMessage {
                channel = CHANNEL
                body = "Broadcast #${i + 1}".toByteArray()
            })
            delay(100)
        }

        delay(2000)

        println("\nResults:")
        for (i in 0 until numSubscribers) {
            println("  Subscriber ${i + 1}: ${counts[i].get()} messages")
        }

        jobs.forEach { it.cancel() }
        println("Done.")
    }
}
