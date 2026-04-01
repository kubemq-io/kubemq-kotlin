package io.kubemq.sdk.examples.commands

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.commandMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-commands-handle-command"
private const val CHANNEL = "kotlin-commands.handle-command"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createCommandsChannel(CHANNEL)

            // Handler that processes command body and builds response with metadata
            val handlerJob = launch {
                client.subscribeToCommands {
                    channel = CHANNEL
                }.take(2).collect { cmd ->
                    val body = String(cmd.body)
                    println("Handler received: $body (tags=${cmd.tags})")

                    // Process the command and build response
                    val success = body.contains("valid")
                    val response = cmd.respond {
                        executed = success
                        metadata = if (success) "processed-ok" else "rejected"
                        this.body = "Result for: $body".toByteArray()
                        tags = mapOf("handler" to "kotlin-handler-1")
                    }
                    client.sendCommandResponse(response)
                    println("Handler sent response: executed=$success")
                }
            }

            delay(500)

            // Send valid command
            val resp1 = client.sendCommand(commandMessage {
                channel = CHANNEL
                body = "valid-command".toByteArray()
                metadata = "test"
                timeoutMs = 10000
                tags["request-type"] = "test"
            })
            println("Command 1 result: executed=${resp1.executed}")

            // Send another command
            val resp2 = client.sendCommand(commandMessage {
                channel = CHANNEL
                body = "invalid-command".toByteArray()
                timeoutMs = 10000
            })
            println("Command 2 result: executed=${resp2.executed}")

            handlerJob.join()
        } finally {
            try { client.deleteCommandsChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
