package io.kubemq.sdk.examples.queues

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.QueueMessagePolicy
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queues-delayed-messages"
private const val CHANNEL = "kotlin-queues.delayed-messages"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Send a message with 3-second delay
        println("Sending message with 3s delay...")
        client.sendQueuesMessage(queueMessage {
            channel = CHANNEL
            body = "Delayed 3s".toByteArray()
            policy = QueueMessagePolicy(delaySeconds = 3)
        })

        // Poll immediately -- expect no message (delay not expired)
        println("Polling immediately (before delay expires)...")
        val earlyResp = client.receiveQueuesMessages {
            channel = CHANNEL
            maxItems = 1
            waitTimeoutMs = 1000
            autoAck = true
        }
        println("Messages before delay: ${earlyResp.messages.size} (expected 0)")

        // Wait for delay to expire
        println("Waiting for delay to expire...")
        delay(3500)

        // Poll after delay -- message should be available
        val lateResp = client.receiveQueuesMessages {
            channel = CHANNEL
            maxItems = 1
            waitTimeoutMs = 5000
            autoAck = true
        }
        println("Messages after delay: ${lateResp.messages.size} (expected 1)")
        lateResp.messages.forEach { println("  Received: ${String(it.body)}") }

        println("Done.")
    }
}
