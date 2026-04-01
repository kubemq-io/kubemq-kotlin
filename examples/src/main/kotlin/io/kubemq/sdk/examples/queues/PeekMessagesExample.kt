package io.kubemq.sdk.examples.queues

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queues-peek-messages"
private const val CHANNEL = "kotlin-queues.peek-messages"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Send messages
        repeat(3) { i ->
            client.sendQueuesMessage(queueMessage {
                channel = CHANNEL
                body = "Peek msg #${i + 1}".toByteArray()
            })
        }
        println("Sent 3 messages.")

        // Peek -- non-destructive read (messages remain in queue)
        val peekResp = client.peekQueueMessages {
            channel = CHANNEL
            maxNumberOfMessages = 5
            waitTimeSeconds = 3
        }
        println("\nPeeked ${peekResp.messages.size} messages (isPeek=${peekResp.isPeek}):")
        peekResp.messages.forEach { println("  ${String(it.body)}") }

        // Verify messages are still in queue by receiving them
        val recvResp = client.receiveQueuesMessages {
            channel = CHANNEL
            maxItems = 10
            waitTimeoutMs = 3000
            autoAck = true
        }
        println("\nAfter peek, received ${recvResp.messages.size} messages (still in queue).")

        println("Done.")
    }
}
