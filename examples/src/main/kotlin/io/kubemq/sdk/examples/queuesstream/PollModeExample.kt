package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-poll-mode"
private const val CHANNEL = "kotlin-queuesstream.poll-mode"

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
                    body = "Poll msg ${i + 1}".toByteArray()
                })
            }

            // Poll in pull mode with maxItems=3
            println("=== Waiting Pull Mode ===\n")
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 3
                waitTimeoutMs = 10000
                autoAck = false
            }

            println("Received ${response.messages.size} messages:")
            response.messages.forEach { msg ->
                println("  ${String(msg.body)}")
                msg.ack()
            }

            // Cleanup remaining
            val cleanup = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 2000
                autoAck = true
            }
            println("\nCleanup: consumed ${cleanup.messages.size} remaining messages.")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
