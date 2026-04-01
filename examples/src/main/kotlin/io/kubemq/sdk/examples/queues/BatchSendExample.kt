package io.kubemq.sdk.examples.queues

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queues-batch-send"
private const val CHANNEL = "kotlin-queues.batch-send"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Batch send using the simple API
        val messages = (1..10).map { i ->
            queueMessage {
                channel = CHANNEL
                body = "Batch item #$i".toByteArray()
                metadata = "batch-job"
            }
        }

        println("Sending ${messages.size} messages in batch...")
        val start = System.currentTimeMillis()
        val results = client.sendQueueMessagesBatch(messages)
        val elapsed = System.currentTimeMillis() - start

        results.forEach { r ->
            println("  Sent: ${r.messageId}, error=${r.isError}")
        }
        println("\n${results.size} messages sent in ${elapsed}ms")
        println("Throughput: ${results.size * 1000.0 / elapsed} msg/s")

        // Cleanup: consume the messages
        val cleanup = client.receiveQueuesMessages {
            channel = CHANNEL
            maxItems = 10
            waitTimeoutMs = 3000
            autoAck = true
        }
        println("Cleanup: consumed ${cleanup.messages.size} messages.")

        println("Done.")
    }
}
