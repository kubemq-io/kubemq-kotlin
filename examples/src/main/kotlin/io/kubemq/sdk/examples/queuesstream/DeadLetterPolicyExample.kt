package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.QueueMessagePolicy
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-dead-letter-policy"
private const val CHANNEL = "kotlin-queuesstream.dead-letter-policy"
private const val DLQ = "kotlin-queuesstream.dead-letter-policy-dlq"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)
            client.createQueuesChannel(DLQ)

            // Send message with DLQ policy (moves to DLQ after 2 rejections)
            client.sendQueuesMessage(queueMessage {
                channel = CHANNEL
                body = "Poison message".toByteArray()
                policy = QueueMessagePolicy(
                    maxReceiveCount = 2,
                    maxReceiveQueue = DLQ,
                )
            })
            println("Sent message with DLQ policy (max 2 attempts).\n")

            // Reject the message; after max attempts it should go to DLQ
            for (attempt in 1..3) {
                val resp = client.receiveQueuesMessages {
                    channel = CHANNEL
                    maxItems = 1
                    waitTimeoutMs = 2000
                    autoAck = false
                }
                if (resp.messages.isNotEmpty()) {
                    println("Attempt $attempt: Rejecting...")
                    resp.messages.first().reject()
                } else {
                    println("Attempt $attempt: No message (moved to DLQ).")
                    break
                }
                delay(500)
            }

            // Read the message from the dead letter queue
            val dlqResp = client.receiveQueuesMessages {
                channel = DLQ
                maxItems = 1
                waitTimeoutMs = 2000
                autoAck = true
            }
            if (dlqResp.messages.isNotEmpty()) {
                println("\nDLQ message: ${String(dlqResp.messages.first().body)}")
            }
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
            try { client.deleteQueuesChannel(DLQ) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
