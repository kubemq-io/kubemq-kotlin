// Expected output:
// Subscribing to commands...
// Sending command...
// Handler received command: <uuid>
// Command response: executed=true

package io.kubemq.sdk.examples.commands

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.commandMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-commands-send-command"
private const val CHANNEL = "kotlin-commands.send-command"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createCommandsChannel(CHANNEL)

            // Subscribe to commands and respond
            val subJob = launch {
                client.subscribeToCommands {
                    channel = CHANNEL
                }.take(1).collect { cmd ->
                    println("Received command: ${String(cmd.body)}")
                    val response = cmd.respond {
                        executed = true
                        metadata = "processed"
                    }
                    client.sendCommandResponse(response)
                    println("Sent response for command ${cmd.id}")
                }
            }

            delay(500)

            // Send a command
            val response = client.sendCommand(commandMessage {
                channel = CHANNEL
                body = "restart-service".toByteArray()
                metadata = "ops"
                timeoutMs = 10000
            })
            println("Command result: executed=${response.executed}, error=${response.error}")

            subJob.join()
        } finally {
            try { client.deleteCommandsChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
