package io.kubemq.sdk.examples.patterns

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-patterns-work-queue"
private const val CHANNEL = "kotlin-patterns.work-queue"

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueuesChannel(CHANNEL)

            // Send tasks to the work queue
            println("Sending 10 tasks to work queue...\n")
            repeat(10) { i ->
                client.sendQueuesMessage(queueMessage {
                    channel = CHANNEL
                    body = "Task #${i + 1}".toByteArray()
                })
            }

            // Worker 1 pulls first batch
            println("Worker 1 pulling batch...")
            val resp1 = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 5
                waitTimeoutMs = 3000
                autoAck = false
            }
            println("  Received: ${resp1.messages.size}")
            resp1.messages.forEach { msg ->
                println("    ${String(msg.body)}")
                msg.ack()
            }

            // Worker 2 pulls remaining batch
            println("\nWorker 2 pulling batch...")
            val resp2 = client.receiveQueuesMessages {
                channel = CHANNEL
                maxItems = 5
                waitTimeoutMs = 3000
                autoAck = false
            }
            println("  Received: ${resp2.messages.size}")
            resp2.messages.forEach { msg ->
                println("    ${String(msg.body)}")
                msg.ack()
            }

            println("\nAll tasks distributed and processed.")
        } finally {
            try { client.deleteQueuesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
