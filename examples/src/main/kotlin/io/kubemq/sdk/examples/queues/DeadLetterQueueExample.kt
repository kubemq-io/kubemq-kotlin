package io.kubemq.sdk.examples.queues

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.QueueMessagePolicy
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queues-dead-letter"
private const val CHANNEL = "kotlin-queues.dead-letter"
private const val DLQ_CHANNEL = "kotlin-queues.dead-letter-dlq"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Send a message with DLQ policy (max 3 receives, then route to DLQ)
        println("Sending message with DLQ policy (max 3 receives)...")
        client.sendQueuesMessage(queueMessage {
            channel = CHANNEL
            body = "DLQ after 3 nacks".toByteArray()
            policy = QueueMessagePolicy(
                maxReceiveCount = 3,
                maxReceiveQueue = DLQ_CHANNEL,
            )
        })

        // Reject the message 3 times
        repeat(3) { attempt ->
            delay(500)
            val resp = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 1
                waitTimeoutMs = 3000
                autoAck = false
            }
            resp.messages.forEach { msg ->
                msg.reject()
                println("Rejected attempt #${attempt + 1}: ${String(msg.body)}")
            }
        }

        // Check the DLQ
        delay(1000)
        val dlqResp = client.receiveQueuesMessages {
            channel = DLQ_CHANNEL
            maxItems = 1
            waitTimeoutMs = 3000
            autoAck = true
        }
        dlqResp.messages.forEach { msg ->
            println("DLQ received: ${String(msg.body)}")
        }

        println("Done.")
    }
}
