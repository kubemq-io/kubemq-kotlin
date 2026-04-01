package io.kubemq.sdk.examples.events

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-events-wildcard-subscription"

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        // Subscribe with '>' wildcard -- receives from all "orders.*" channels
        val wildcardJob = launch {
            client.subscribeToEvents {
                channel = "orders.>"
            }.take(3).collect { msg ->
                println("[wildcard >] ${msg.channel}: ${String(msg.body)}")
            }
        }

        delay(500)

        // Publish to specific channels
        client.publishEvent(eventMessage {
            channel = "orders.created"
            body = "Order 1001 created".toByteArray()
        })
        client.publishEvent(eventMessage {
            channel = "orders.shipped"
            body = "Order 1002 shipped".toByteArray()
        })
        client.publishEvent(eventMessage {
            channel = "orders.delivered"
            body = "Order 1003 delivered".toByteArray()
        })

        wildcardJob.join()
        println("Done.")
    }
}
