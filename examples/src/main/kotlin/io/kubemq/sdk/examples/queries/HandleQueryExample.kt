package io.kubemq.sdk.examples.queries

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.queryMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-queries-handle-query"
private const val CHANNEL = "kotlin-queries.handle-query"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueriesChannel(CHANNEL)

            // Handler that parses query body and builds rich response
            val handlerJob = launch {
                client.subscribeToQueries {
                    channel = CHANNEL
                }.take(1).collect { query ->
                    val requestBody = String(query.body)
                    println("Handler received query: $requestBody")
                    println("  Tags: ${query.tags}")

                    // Parse and process
                    val responseBody = """{"userId": 123, "name": "John Doe"}"""
                    val response = query.respond {
                        executed = true
                        metadata = "application/json"
                        body = responseBody.toByteArray()
                        tags = mapOf("status" to "200", "handler" to "kotlin-handler")
                    }
                    client.sendQueryResponse(response)
                    println("Handler sent response with tags: ${response.tags}")
                }
            }

            delay(500)

            // Send query
            val resp = client.sendQuery(queryMessage {
                channel = CHANNEL
                body = "getUser:123".toByteArray()
                metadata = "json"
                timeoutMs = 10000
            })
            println("Response body: ${String(resp.body)}")
            println("Response metadata: ${resp.metadata}")
            println("Response tags: ${resp.tags}")

            handlerJob.join()
        } finally {
            try { client.deleteQueriesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
