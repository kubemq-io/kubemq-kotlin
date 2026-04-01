package io.kubemq.sdk.burnin.patterns

import io.kubemq.sdk.burnin.PatternConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.queues.QueuesClient
import io.kubemq.sdk.queues.queueMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QueueSimplePattern(
    brokerAddress: String,
    config: PatternConfig,
) : BasePattern("queue_simple", brokerAddress, config) {

    private var sender: QueuesClient? = null
    private var receiver: QueuesClient? = null

    override suspend fun createChannels() {
        val client = createClient("send")
        sender = client
        for (ch in channelNames) {
            client.createQueuesChannel(ch)
        }
    }

    override suspend fun deleteChannels() {
        val client = sender ?: createClient("cleanup")
        for (ch in channelNames) {
            try {
                client.deleteQueuesChannel(ch)
            } catch (e: Exception) {
                logger.debug("Delete channel {} failed: {}", ch, e.message)
            }
        }
    }

    override suspend fun run() {
        val scope = scope ?: return

        // Start receivers
        receiver = createClient("recv")
        for (ch in channelNames) {
            scope.launch {
                while (running) {
                    try {
                        val response = receiver!!.receiveQueueMessages {
                            channel = ch
                            maxNumberOfMessages = 10
                            waitTimeSeconds = 5
                        }
                        if (!response.isError && response.messages.isNotEmpty()) {
                            recordReceived(ch, response.messages.size.toLong())
                        }
                    } catch (_: CancellationException) {
                        break
                    } catch (e: Exception) {
                        recordError(ch)
                        logger.debug("Receive error on {}: {}", ch, e.message)
                        delay(1000)
                    }
                }
            }
        }

        // Start senders
        for (ch in channelNames) {
            scope.launch {
                rateLimitedLoop(this, config.rate) {
                    try {
                        sender!!.sendQueueMessage(queueMessage {
                            channel = ch
                            metadata = "burnin"
                            body = generatePayload()
                        })
                        recordSent(ch)
                    } catch (_: CancellationException) {
                        throw CancellationException()
                    } catch (e: Exception) {
                        recordError(ch)
                        logger.debug("Send error on {}: {}", ch, e.message)
                    }
                }
            }
        }
    }

    override suspend fun closeClients() {
        receiver?.close()
        sender?.close()
        receiver = null
        sender = null
    }

    private fun createClient(suffix: String): QueuesClient = KubeMQClient.queues {
        address = brokerAddress
        clientId = "burnin-queue-simple-$suffix"
    }

    private fun generatePayload(): ByteArray = ByteArray(256) { it.toByte() }
}
