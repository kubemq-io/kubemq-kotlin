package io.kubemq.sdk.examples.queries

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.queryMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queries-consumer-group"
private const val CHANNEL = "kotlin-queries.consumer-group"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueriesChannel(CHANNEL)

            // 3 handlers in the same consumer group
            val group = "query-handlers"
            val numWorkers = 3
            val counts = Array(numWorkers) { AtomicInteger(0) }

            val jobs = (0 until numWorkers).map { i ->
                launch {
                    client.subscribeToQueries {
                        channel = CHANNEL
                        this.group = group
                    }.collect { query ->
                        counts[i].incrementAndGet()
                        val response = query.respond {
                            executed = true
                            body = "Response from worker ${i + 1}".toByteArray()
                            tags = mapOf("worker" to "${i + 1}")
                        }
                        client.sendQueryResponse(response)
                    }
                }
            }

            delay(500)

            // Send 9 queries
            println("Sending 9 queries to group '$group'...\n")
            repeat(9) { i ->
                try {
                    val resp = client.sendQuery(queryMessage {
                        channel = CHANNEL
                        body = "Query #${i + 1}".toByteArray()
                        timeoutMs = 10000
                    })
                    println("Query ${i + 1}: handled by worker ${resp.tags["worker"]}")
                } catch (e: Exception) {
                    println("Query ${i + 1} error: ${e.message}")
                }
            }

            delay(500)

            println("\nDistribution:")
            for (i in 0 until numWorkers) {
                println("  Worker ${i + 1}: ${counts[i].get()}")
            }

            jobs.forEach { it.cancel() }
        } finally {
            try { client.deleteQueriesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
