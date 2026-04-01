package io.kubemq.sdk.examples.commands

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.commandMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS = "localhost:50000"
private const val CLIENT_ID = "kotlin-commands-consumer-group"
private const val CHANNEL = "kotlin-commands.consumer-group"

fun main() = runBlocking {
    val client = KubeMQClient.cq {
        address = ADDRESS
        clientId = CLIENT_ID
    }

    client.use {
        try {
            client.createCommandsChannel(CHANNEL)

            // Subscribe 3 handlers in the same consumer group (load-balanced)
            val group = "command-handlers"
            val numWorkers = 3
            val counts = Array(numWorkers) { AtomicInteger(0) }

            val jobs = (0 until numWorkers).map { i ->
                launch {
                    client.subscribeToCommands {
                        channel = CHANNEL
                        this.group = group
                    }.collect { cmd ->
                        counts[i].incrementAndGet()
                        val response = cmd.respond { executed = true }
                        client.sendCommandResponse(response)
                    }
                }
            }

            delay(500)

            // Send 9 commands to the group (distributed across workers)
            println("Sending 9 commands to group '$group'...\n")
            repeat(9) { i ->
                try {
                    client.sendCommand(commandMessage {
                        channel = CHANNEL
                        body = "Cmd #${i + 1}".toByteArray()
                        timeoutMs = 10000
                    })
                } catch (e: Exception) {
                    println("Command ${i + 1} error: ${e.message}")
                }
            }

            delay(1000)

            println("Distribution:")
            for (i in 0 until numWorkers) {
                println("  Worker ${i + 1}: ${counts[i].get()}")
            }

            jobs.forEach { it.cancel() }
        } finally {
            try { client.deleteCommandsChannel(CHANNEL) } catch (_: Exception) {}
        }
        println("Done.")
    }
}
