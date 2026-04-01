package io.kubemq.sdk.examples.queuesstream

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queuesstream-visibility-timeout"
private const val CHANNEL = "kotlin-queuesstream.visibility-timeout"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send a test message
            client.sendQueuesMessage(queueMessage {
                channel = CHANNEL
                body = "Visibility test".toByteArray()
            })

            // Poll with manual ack
            // NOTE: The Kotlin SDK does not support visibilitySeconds on QueueReceiveConfig
            // (the proto QueuesDownstreamRequest has no such field). This example demonstrates
            // the receive + delayed ack pattern instead.
            val response = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 1
                waitTimeoutMs = 5000
                autoAck = false
            }

            if (response.messages.isNotEmpty()) {
                val msg = response.messages.first()
                println("Received: ${String(msg.body)}")

                // Simulate processing with delay
                println("Processing (simulating work)...")
                delay(2000)

                // TODO: Kotlin SDK does not support extendVisibilityTimer().
                // In the Java SDK, msg.extendVisibilityTimer(3) would extend the
                // visibility window. When this API is available in Kotlin, use it here.
                println("Note: extendVisibilityTimer() not available in Kotlin SDK.")

                // Acknowledge after processing
                msg.ack()
                println("Acknowledged after processing.")
            }
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
