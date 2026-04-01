package io.kubemq.sdk.examples.management

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-management-generic"

/**
 * The Kotlin SDK does not expose a public generic `createChannel(name, type)` API.
 * Channel management uses typed methods on each client. This example demonstrates
 * the typed approach that achieves the same result as the Java generic API.
 */
fun main() = runBlocking {
    // --- PubSubClient ---
    val pubsub = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = "$CLIENT_ID-pubsub"
    }
    pubsub.use { ps ->
        ps.createEventsChannel("generic-test.events")
        println("PubSubClient: created events channel")

        ps.createEventsStoreChannel("generic-test.store")
        println("PubSubClient: created events_store channel")

        val channels = ps.listEventsChannels("generic-test")
        println("PubSubClient: listed events channels (${channels.size})")

        ps.deleteEventsChannel("generic-test.events")
        ps.deleteEventsStoreChannel("generic-test.store")
        println("PubSubClient: deleted channels")
    }

    // --- CQClient ---
    val cq = KubeMQClient.cq {
        address = ADDRESS
        clientId = "$CLIENT_ID-cq"
    }
    cq.use { c ->
        c.createCommandsChannel("generic-test.commands")
        println("CQClient: created commands channel")

        c.createQueriesChannel("generic-test.queries")
        println("CQClient: created queries channel")

        val cmds = c.listCommandsChannels("generic-test")
        println("CQClient: listed commands channels (${cmds.size})")

        c.deleteCommandsChannel("generic-test.commands")
        c.deleteQueriesChannel("generic-test.queries")
        println("CQClient: deleted channels")
    }

    // --- QueuesClient ---
    val queues = KubeMQClient.queues {
        address = ADDRESS
        clientId = "$CLIENT_ID-queues"
    }
    queues.use { q ->
        q.createQueuesChannel("generic-test.queues")
        println("QueuesClient: created queues channel")

        val qChs = q.listQueuesChannels("generic-test")
        println("QueuesClient: listed queues channels (${qChs.size})")

        q.deleteQueuesChannel("generic-test.queues")
        println("QueuesClient: deleted queues channel")
    }

    println("\nGeneric channel management examples completed.")
}
