package io.kubemq.sdk.examples.management

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-management-delete-channel"

fun main() = runBlocking {
    val pubSub = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }
    val queues = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    pubSub.use { ps ->
        queues.use { q ->
            // Create channels
            ps.createEventsChannel("mgmt.delete.events")
            q.createQueuesChannel("mgmt.delete.queues")
            println("Created 2 channels.")

            // Delete channels
            ps.deleteEventsChannel("mgmt.delete.events")
            println("Deleted events channel: mgmt.delete.events")

            q.deleteQueuesChannel("mgmt.delete.queues")
            println("Deleted queues channel: mgmt.delete.queues")

            println("Done.")
        }
    }
}
