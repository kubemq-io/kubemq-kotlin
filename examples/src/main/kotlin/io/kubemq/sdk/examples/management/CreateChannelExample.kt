package io.kubemq.sdk.examples.management

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-management-create-channel"

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
                    // Create channels for all types
                    ps.createEventsChannel("mgmt.create.events")
                    println("Created events channel")

                    ps.createEventsStoreChannel("mgmt.create.events-store")
                    println("Created events store channel")

                    q.createQueuesChannel("mgmt.create.queues")
                    println("Created queues channel")

                    c.createCommandsChannel("mgmt.create.commands")
                    println("Created commands channel")

                    c.createQueriesChannel("mgmt.create.queries")
                    println("Created queries channel")

                    println("\nAll 5 channel types created successfully.")
                } finally {
                    // Cleanup
                    try { ps.deleteEventsChannel("mgmt.create.events") } catch (_: Exception) {}
                    try { ps.deleteEventsStoreChannel("mgmt.create.events-store") } catch (_: Exception) {}
                    try { q.deleteQueuesChannel("mgmt.create.queues") } catch (_: Exception) {}
                    try { c.deleteCommandsChannel("mgmt.create.commands") } catch (_: Exception) {}
                    try { c.deleteQueriesChannel("mgmt.create.queries") } catch (_: Exception) {}
                }
                println("Done.")
            }
        }
    }
}
