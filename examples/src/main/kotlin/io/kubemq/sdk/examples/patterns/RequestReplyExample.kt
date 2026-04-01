package io.kubemq.sdk.examples.patterns

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.queryMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-patterns-request-reply"
private const val CHANNEL = "kotlin-patterns.request-reply"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createQueriesChannel(CHANNEL)

            // Service handler (receives request, sends response)
            val serviceJob = launch {
                client.subscribeToQueries {
                    channel = CHANNEL
                }.collect { request ->
                    val requestBody = String(request.body)
                    println("  [Service] Received: $requestBody")

                    val responseBody = """{"userId": 123, "name": "John Doe"}"""
                    val response = request.respond {
                        executed = true
                        body = responseBody.toByteArray()
                        tags = mapOf("status" to "200")
                    }
                    client.sendQueryResponse(response)
                }
            }

            delay(300)

            // Client sends request and waits for reply
            println("Client sending request...")
            val response = client.sendQuery(queryMessage {
                channel = CHANNEL
                body = "getUser:123".toByteArray()
                timeoutMs = 10000
            })
            println("Response: ${String(response.body)}")
            println("Status: ${response.tags["status"]}")

            serviceJob.cancel()
        } finally {
            try { client.deleteQueriesChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
