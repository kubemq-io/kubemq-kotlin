package io.kubemq.sdk.examples.management

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-management-purge-queue"
private const val CHANNEL = "kotlin-mgmt.purge-queue"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send several messages
            for (i in 0 until 5) {
                client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "purge-msg-$i".toByteArray()
                })
            }
            println("Sent 5 messages to queue")

            // Purge all messages from the queue
            val purged = client.purgeQueuesChannel(CHANNEL)
            println("Purged queue: $CHANNEL ($purged messages)")

            // Verify empty
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 2000
                autoAck = true
            }
            println("After purge: ${response.messages.size} messages")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
