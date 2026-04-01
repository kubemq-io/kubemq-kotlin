package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-stream-send"
private const val CHANNEL = "kotlin-queuesstream.stream-send"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            println("Sending messages via stream...\n")
            val start = System.currentTimeMillis()

            // Send messages in a loop via stream
            repeat(10) { i ->
                val result = client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "Stream message #${i + 1}".toByteArray()
                })
                println("  Sent #${i + 1} -> ${result.messageId}")
            }

            val elapsed = System.currentTimeMillis() - start
            println("\n10 messages sent in ${elapsed}ms")

            // Cleanup
            val cleanup = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 2000
                autoAck = true
            }
            println("Cleanup: consumed ${cleanup.messages.size} messages.")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
