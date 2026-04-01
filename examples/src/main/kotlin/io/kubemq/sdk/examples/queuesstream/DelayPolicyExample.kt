package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.QueueMessagePolicy
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-delay-policy"
private const val CHANNEL = "kotlin-queuesstream.delay-policy"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send messages with different delay values
            val delays = intArrayOf(1, 3, 5)
            for (delay in delays) {
                client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "Delay ${delay}s".toByteArray()
                    policy = QueueMessagePolicy(delaySeconds = delay)
                })
                println("Sent message with ${delay}s delay.")
            }

            // Poll as messages become available after their delay
            println("\nPolling as messages become available...")
            repeat(3) {
                val response = client.receiveQueuesMessages {
                    channel = CHANNEL
                    maxItems = 1
                    waitTimeoutMs = 10000
                    autoAck = true
                }
                if (response.messages.isNotEmpty()) {
                    println("  Received: ${String(response.messages.first().body)}")
                }
            }
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
