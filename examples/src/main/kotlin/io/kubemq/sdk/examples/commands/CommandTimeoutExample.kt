package io.kubemq.sdk.examples.commands

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.commandMessage
import io.kubemq.sdk.exception.KubeMQException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-commands-command-timeout"
private const val CHANNEL = "kotlin-commands.command-timeout"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createCommandsChannel(CHANNEL)

            // Subscribe to handle commands (handler intentionally delays 3s)
            val subJob = launch {
                client.subscribeToCommands {
                    channel = CHANNEL
                }.collect { cmd ->
                    println("Handler received: ${String(cmd.body)}, delaying 3s...")
                    delay(3000)
                    val response = cmd.respond {
                        executed = true
                    }
                    client.sendCommandResponse(response)
                    println("Handler sent response.")
                }
            }

            delay(500)

            // Send command with short timeout (expect timeout)
            println("Sending command with 1s timeout (handler takes 3s)...")
            try {
                val resp = client.sendCommand(commandMessage {
                    channel = CHANNEL
                    body = "Slow command".toByteArray()
                    timeoutMs = 1000
                })
                if (!resp.executed && resp.error.isNotEmpty()) {
                    println("Timeout (expected): ${resp.error}")
                    println("  executed=${resp.executed}")
                } else {
                    println("Unexpected success: executed=${resp.executed}")
                }
            } catch (e: Exception) {
                println("Exception (timeout): ${e.message}")
            }

            // Wait for handler to finish before sending second command
            delay(4000)

            // Send command with sufficient timeout (expect success)
            println("\nSending command with 5s timeout (handler takes 3s)...")
            try {
                val resp = client.sendCommand(commandMessage {
                    channel = CHANNEL
                    body = "Normal command".toByteArray()
                    timeoutMs = 5000
                })
                println("Success: executed=${resp.executed}")
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }

            subJob.cancel()
        } finally {
            try { client.deleteCommandsChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
