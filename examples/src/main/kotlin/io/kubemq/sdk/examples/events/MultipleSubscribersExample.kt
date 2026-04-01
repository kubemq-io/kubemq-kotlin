package io.kubemq.sdk.examples.events

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-events-multiple-subscribers"
private const val CHANNEL = "kotlin-events.multiple-subscribers"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        val numMessages = 3
        val sub1Count = AtomicInteger(0)
        val sub2Count = AtomicInteger(0)
        val totalReceived = AtomicInteger(0)

        // Two independent subscribers (no group = broadcast to all)
        val job1 = launch {
            client.subscribeToEvents {
                channel = CHANNEL
            }.collect { msg ->
                sub1Count.incrementAndGet()
                println("  Subscriber A: ${String(msg.body)}")
                totalReceived.incrementAndGet()
            }
        }

        val job2 = launch {
            client.subscribeToEvents {
                channel = CHANNEL
            }.collect { msg ->
                sub2Count.incrementAndGet()
                println("  Subscriber B: ${String(msg.body)}")
                totalReceived.incrementAndGet()
            }
        }

        println("Two independent subscribers created (broadcast mode).\n")
        delay(500)

        // Send event messages (each subscriber receives all)
        repeat(numMessages) { i ->
            client.publishEvent(eventMessage {
                channel = CHANNEL
                body = "Broadcast message #${i + 1}".toByteArray()
            })
            delay(100)
        }

        delay(2000)

        println("\nResults:")
        println("  Subscriber A received: ${sub1Count.get()}")
        println("  Subscriber B received: ${sub2Count.get()}")
        println("  Both received all $numMessages messages (broadcast).")

        job1.cancel()
        job2.cancel()
        println("Done.")
    }
}
