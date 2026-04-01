package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-nack-all"
private const val CHANNEL = "kotlin-queuesstream.nack-all"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send messages
            repeat(3) { i ->
                client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "Nack msg ${i + 1}".toByteArray()
                })
            }

            // Poll for messages
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 5000
                autoAck = false
            }

            println("Received ${response.messages.size} messages.")
            // Reject all messages (return them to queue for redelivery)
            client.nackAllQueuesMessages(response)
            println("All messages rejected via nackAll().")
            println("Messages returned to queue for redelivery.")

            // Cleanup: consume rejected messages
            val cleanup = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 1000
                autoAck = true
            }
            println("Cleanup: ${cleanup.messages.size} messages consumed.")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
