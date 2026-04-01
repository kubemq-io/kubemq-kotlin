// Expected output:
// State: Idle
// State: Connecting
// State: Ready
// Connected to localhost
// (If broker restarts: State: Reconnecting(attempt=1))
// (After reconnect: State: Ready, Reconnected after 1 attempts)

package io.kubemq.sdk.examples.errorhandling

import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = KubeMQClient.pubSub {
        address = "localhost:50000"
        clientId = "reconnect-example"
        reconnection {
            initialBackoffMs = 1000
            maxBackoffMs = 30_000
            multiplier = 2.0
            maxRetries = 10 // 0 = infinite retries
        }
    }

    client.use {
        // Monitor connection state changes
        val monitorJob = launch {
            client.connectionState
                .takeWhile { it !is ConnectionState.Closed }
                .collect { state ->
                    when (state) {
                        is ConnectionState.Idle ->
                            println("[state] Idle")
                        is ConnectionState.Connecting ->
                            println("[state] Connecting...")
                        is ConnectionState.Ready ->
                            println("[state] Connected and ready")
                        is ConnectionState.Reconnecting ->
                            println("[state] Reconnecting, attempt #${state.attempt}")
                        is ConnectionState.Closed ->
                            println("[state] Closed")
                    }
                }
        }

        // Initial connection
        val info = client.ping()
        println("Connected to ${info.host} v${info.version}")

        // Publish events periodically -- if broker goes down, reconnection
        // will kick in automatically. Outgoing calls will block until
        // the connection is re-established.
        println("\nPublishing events (stop broker to see reconnection)...")
        repeat(10) { i ->
            try {
                client.publishEvent(eventMessage {
                    channel = "reconnect.test"
                    body = "Message #$i".toByteArray()
                })
                println("Published #$i")
            } catch (e: Exception) {
                println("Publish #$i failed: ${e.message}")
            }
            delay(2000)
        }

        monitorJob.cancel()
        println("Done.")
    }
}
