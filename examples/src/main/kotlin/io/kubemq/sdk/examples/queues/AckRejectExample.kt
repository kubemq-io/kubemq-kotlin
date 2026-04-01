package io.kubemq.sdk.examples.queues

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queues-ack-reject"
private const val CHANNEL = "kotlin-queues.ack-reject"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Send multiple messages
        val bodies = listOf("important-task", "bad-task", "another-good-task")
        bodies.forEach { body ->
            client.sendQueuesMessage(queueMessage {
                channel = CHANNEL
                this.body = body.toByteArray()
            })
            println("Sent: $body")
        }

        // Receive and selectively ack/reject
        val response = client.receiveQueuesMessages {
            channel = CHANNEL
            maxItems = 10
            waitTimeoutMs = 5000
            autoAck = false
        }

        for (msg in response.messages) {
            val body = String(msg.body)
            if (body.contains("bad")) {
                msg.reject()
                println("Rejected: $body")
            } else {
                msg.ack()
                println("Acked: $body")
            }
        }

        // Cleanup rejected messages
        val cleanup = client.receiveQueuesMessages {
            channel = CHANNEL
            maxItems = 10
            waitTimeoutMs = 1000
            autoAck = true
        }
        println("Cleanup: consumed ${cleanup.messages.size} rejected messages.")

        println("Done.")
    }
}
