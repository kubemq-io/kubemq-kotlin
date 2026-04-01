package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-auto-ack"
private const val CHANNEL = "kotlin-queuesstream.auto-ack"

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
                    body = "Auto-ack msg ${i + 1}".toByteArray()
                })
            }

            // Poll with auto-ack enabled (messages acknowledged automatically)
            println("Polling with autoAck=true...\n")
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 10
                waitTimeoutMs = 5000
                autoAck = true
            }

            response.messages.forEach { msg ->
                println("  Received (auto-acked): ${String(msg.body)}")
            }
            println("\n${response.messages.size} messages auto-acknowledged.")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
