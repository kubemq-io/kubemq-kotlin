package io.kubemq.sdk.examples.management

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-management-list-channels"

fun main() = runBlocking {
    val pubSub = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }
    val queues = KubeMQClient.queues {
        address = ADDRESS
        clientId = CLIENT_ID
    }
    val cq = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    pubSub.use { ps ->
        queues.use { q ->
            cq.use { c ->
                try {
                    // Create some channels for listing
                    ps.createEventsChannel("mgmt.list.events1")
                    ps.createEventsChannel("mgmt.list.events2")
                    q.createQueuesChannel("mgmt.list.queues1")

                    // List channels by type with prefix filter
                    val eventsChannels = ps.listEventsChannels("mgmt.list")
                    println("Events channels (prefix 'mgmt.list'): ${eventsChannels.size}")
                    eventsChannels.forEach { println("  ${it.name}") }

                    val eventsStoreChannels = ps.listEventsStoreChannels("mgmt.list")
                    println("Events store channels: ${eventsStoreChannels.size}")

                    val queuesChannels = q.listQueuesChannels("mgmt.list")
                    println("Queues channels: ${queuesChannels.size}")
                    queuesChannels.forEach { println("  ${it.name}") }

                    val commandsChannels = c.listCommandsChannels("mgmt.list")
                    println("Commands channels: ${commandsChannels.size}")

                    val queriesChannels = c.listQueriesChannels("mgmt.list")
                    println("Queries channels: ${queriesChannels.size}")
                } finally {
                    try { ps.deleteEventsChannel("mgmt.list.events1") } catch (_: Exception) {}
                    try { ps.deleteEventsChannel("mgmt.list.events2") } catch (_: Exception) {}
                    try { q.deleteQueuesChannel("mgmt.list.queues1") } catch (_: Exception) {}
                }
                println("Done.")
            }
        }
    }
}
