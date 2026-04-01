package io.kubemq.sdk.examples.queries

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.queryMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queries-cached-query"
private const val CHANNEL = "kotlin-queries.cached-query"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueriesChannel(CHANNEL)

            // Subscribe to queries and respond
            val subJob = launch {
                client.subscribeToQueries {
                    channel = CHANNEL
                }.take(2).collect { query ->
                    println("Handler received query: ${String(query.body)}")
                    val response = query.respond {
                        executed = true
                        body = """{"user":"alice","age":30}""".toByteArray()
                        metadata = "from-handler"
                    }
                    client.sendQueryResponse(response)
                }
            }

            delay(500)

            // First query -- hits handler, result cached with key
            val resp1 = client.sendQuery(queryMessage {
                channel = CHANNEL
                body = "get-user:alice".toByteArray()
                timeoutMs = 10000
                cacheKey = "user:alice"
                cacheTtlSeconds = 60
            })
            println("Query 1: cacheHit=${resp1.cacheHit}, body=${String(resp1.body)}")

            // Second query with same cache key -- may return cached result
            val resp2 = client.sendQuery(queryMessage {
                channel = CHANNEL
                body = "get-user:alice".toByteArray()
                timeoutMs = 10000
                cacheKey = "user:alice"
                cacheTtlSeconds = 60
            })
            println("Query 2: cacheHit=${resp2.cacheHit}, body=${String(resp2.body)}")

            subJob.join()
        } finally {
            try { client.deleteQueriesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
