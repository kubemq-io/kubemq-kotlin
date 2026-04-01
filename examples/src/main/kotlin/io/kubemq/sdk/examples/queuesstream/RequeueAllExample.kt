package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-requeue-all"
private const val CHANNEL = "kotlin-queuesstream.requeue-all"
private const val REQUEUE_CHANNEL = "kotlin-queuesstream.requeue-all-dest"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)
            client.createQueuesChannel(REQUEUE_CHANNEL)

            // Send messages to source queue
            repeat(3) { i ->
                client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "Requeue msg ${i + 1}".toByteArray()
                })
            }

            // Poll from source queue
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 5000
                autoAck = false
            }

            println("Received ${response.messages.size} messages from source.")
            // Requeue all to destination channel
            client.reQueueAllMessages(response, REQUEUE_CHANNEL)
            println("All messages requeued to: $REQUEUE_CHANNEL")

            // Verify messages arrived in destination
            val destResp = client.receiveQueuesMessages {
                channel = REQUEUE_CHANNEL
                maxItems = 10
                waitTimeoutMs = 2000
                autoAck = true
            }
            println("Destination queue received: ${destResp.messages.size} messages.")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
            try { client.deleteQueuesChannel(REQUEUE_CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
