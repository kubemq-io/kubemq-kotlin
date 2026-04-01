// Expected output:
// Sending queue message...
// Sent: messageId=<uuid>
// Receiving messages...
// Received 1 message(s)
// Message: channel=queues.send-receive-example, body=Hello Queue!

package io.kubemq.sdk.examples.queues

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = KubeMQClient.queues {
        address = "localhost:50000"
        clientId = "queues-example"
    }

    client.use {
        val channel = "queues.example"

        // Send a message
        val sendResult = client.sendQueuesMessage(queueMessage {
            this.channel = channel
            body = "Hello from queue".toByteArray()
            metadata = "task"
            tags["priority"] = "high"
        })
        println("Sent: id=${sendResult.messageId}, error=${sendResult.error}")

        // Receive with manual ack
        val response = client.receiveQueuesMessages {
            this.channel = channel
            maxItems = 10
            waitTimeoutMs = 5000
            autoAck = false
        }

        if (response.isError) {
            println("Receive error: ${response.error}")
            return@runBlocking
        }

        for (msg in response.messages) {
            println("Received: ${String(msg.body)}, tags=${msg.tags}")
            // Acknowledge the message
            msg.ack()
            println("  Acked message ${msg.id}")
        }

        // Send another and reject it
        client.sendQueuesMessage(queueMessage {
            this.channel = channel
            body = "Will be rejected".toByteArray()
        })
        val resp2 = client.receiveQueuesMessages {
            this.channel = channel
            maxItems = 1
            waitTimeoutMs = 5000
        }
        resp2.messages.forEach { msg ->
            msg.reject()
            println("Rejected message ${msg.id}")
        }

        println("Done.")
    }
}
