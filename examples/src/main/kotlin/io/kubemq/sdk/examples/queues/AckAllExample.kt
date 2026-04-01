package io.kubemq.sdk.examples.queues

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queues-ack-all"
private const val CHANNEL = "kotlin-queues.ack-all"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Send batch of messages
        val messages = (1..5).map { i ->
            queueMessage {
                channel = CHANNEL
                body = "Batch msg #$i".toByteArray()
            }
        }
        client.sendQueueMessagesBatch(messages)
        println("Sent ${messages.size} messages.")

        // Receive with manual ack
        val response = client.receiveQueuesMessages {
            channel = CHANNEL
            maxItems = 10
            waitTimeoutMs = 5000
            autoAck = false
        }

        println("Received ${response.messages.size} messages:")
        response.messages.forEach { println("  ${String(it.body)}") }

        // Ack all in one call
        if (response.messages.isNotEmpty()) {
            client.ackAllQueuesMessages(response)
            println("\nAcked all ${response.messages.size} messages at once.")
        }

        println("Done.")
    }
}
