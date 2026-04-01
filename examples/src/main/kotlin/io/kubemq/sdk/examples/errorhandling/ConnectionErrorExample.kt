package io.kubemq.sdk.examples.errorhandling

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-errorhandling-connection-error"

fun main() = runBlocking {
    // 1. Bad address error
    println("=== Bad Address Error ===\n")
    try {
        val badClient = KubeMQClient.pubSub {
            address = "localhost:59999"
            clientId = CLIENT_ID
        }
        badClient.use {
            it.ping()
        }
    } catch (e: KubeMQException) {
        when (e) {
            is KubeMQException.Connection ->
                println("Connection error (expected): ${e.message}")
            else ->
                println("Error: ${e.message} (code=${e.code}, category=${e.category})")
        }
        println("  retryable=${e.isRetryable}")
    } catch (e: Exception) {
        println("Error (expected): ${e.javaClass.simpleName}: ${e.message}")
    }

    // 2. Successful connection
    println("\n=== Successful Connection ===\n")
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        val info = client.ping()
        println("Connected to ${info.host} v${info.version}")

        // 3. Typed exception handling
        println("\n=== Typed Exception Handling ===\n")
        try {
            client.publishEvent(eventMessage {
                channel = "" // invalid
                body = "test".toByteArray()
            })
        } catch (e: KubeMQException) {
            when (e) {
                is KubeMQException.Validation ->
                    println("Validation: ${e.message} (op=${e.operation})")
                is KubeMQException.Connection ->
                    println("Connection: ${e.message} (retryable=${e.isRetryable})")
                is KubeMQException.Authentication ->
                    println("Auth: ${e.message}")
                is KubeMQException.Authorization ->
                    println("Authz: ${e.message} (channel=${e.channel})")
                is KubeMQException.Timeout ->
                    println("Timeout: ${e.message} (duration=${e.duration})")
                is KubeMQException.Throttling ->
                    println("Throttled: ${e.message}")
                is KubeMQException.Transport ->
                    println("Transport: ${e.message}")
                is KubeMQException.Server ->
                    println("Server: ${e.message} (code=${e.statusCode})")
                is KubeMQException.ClientClosed ->
                    println("Client closed: ${e.message}")
                is KubeMQException.StreamBroken ->
                    println("Stream broken: ${e.message}")
            }
            println("  code=${e.code}, category=${e.category}, retryable=${e.isRetryable}")
        }

        // 4. Retryable check
        println("\n=== Retryable Check ===\n")
        try {
            client.publishEvent(eventMessage {
                channel = "error.test"
                body = "test".toByteArray()
            })
            println("Published successfully")
        } catch (e: KubeMQException) {
            if (e.isRetryable) {
                println("Error is retryable (${e.code}), would retry...")
            } else {
                println("Error is NOT retryable (${e.code}), failing immediately")
            }
        }

        println("\nDone.")
    }
}
