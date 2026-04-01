package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.QueueMessagePolicy
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-expiration-policy"
private const val CHANNEL = "kotlin-queuesstream.expiration-policy"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send message with 3s expiration
            val expirationSeconds = 3
            client.sendQueuesMessage(queueMessage {
                channel = CHANNEL
                body = "Expiring message".toByteArray()
                policy = QueueMessagePolicy(expirationSeconds = expirationSeconds)
            })
            println("Sent message with ${expirationSeconds}s expiration.")

            // Wait for message to expire
            println("Waiting ${expirationSeconds + 2}s for expiration...")
            delay(((expirationSeconds + 2) * 1000).toLong())

            // Poll after expiration (expect no messages)
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 1
                waitTimeoutMs = 1000
                autoAck = true
            }
            println("Messages after expiration: ${response.messages.size} (expected 0)")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
