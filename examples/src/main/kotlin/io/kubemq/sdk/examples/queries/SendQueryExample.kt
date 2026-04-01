// Expected output:
// Subscribing to queries...
// Sending query...
// Handler received query: <uuid>
// Query response: executed=true, body={"answer": 42}

package io.kubemq.sdk.examples.queries

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.queryMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queries-send-query"
private const val CHANNEL = "kotlin-queries.send-query"

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
                }.take(1).collect { query ->
                    println("Received query: ${String(query.body)}")
                    val response = query.respond {
                        executed = true
                        metadata = "result"
                        body = """{"user":"alice","age":30}""".toByteArray()
                    }
                    client.sendQueryResponse(response)
                    println("Sent response for query ${query.id}")
                }
            }

            delay(500)

            // Send a query
            val resp = client.sendQuery(queryMessage {
                channel = CHANNEL
                body = "get-user:alice".toByteArray()
                metadata = "user-lookup"
                timeoutMs = 10000
                tags["request-type"] = "lookup"
            })
            println("Query result: ${String(resp.body)}, cacheHit=${resp.cacheHit}")

            subJob.join()
        } finally {
            try { client.deleteQueriesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
