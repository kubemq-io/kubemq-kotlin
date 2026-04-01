package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-ack-range"
private const val CHANNEL = "kotlin-queuesstream.ack-range"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send messages
            repeat(5) { i ->
                client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "Range msg ${i + 1}".toByteArray()
                })
            }

            // Poll for a batch of messages
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 5
                waitTimeoutMs = 5000
                autoAck = false
            }

            println("Received ${response.messages.size} messages:")
            response.messages.forEach { msg ->
                println("  ${String(msg.body)}")
            }

            // Acknowledge all messages in the batch at once
            client.ackAllQueuesMessages(response)
            println("\nAll ${response.messages.size} messages acked via ackAll().")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
