package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-stream-receive"
private const val CHANNEL = "kotlin-queuesstream.stream-receive"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send messages to the queue
            repeat(5) { i ->
                client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "Message ${i + 1}".toByteArray()
                })
            }

            // Poll for messages via stream
            println("Receiving messages via stream poll...\n")
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 5000
                autoAck = false
            }

            // Process each message and acknowledge it
            response.messages.forEach { msg ->
                println("  Received: ${String(msg.body)}")
                msg.ack()
            }
            println("\nReceived and acked ${response.messages.size} messages.")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
