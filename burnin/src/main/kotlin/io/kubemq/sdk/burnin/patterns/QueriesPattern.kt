package io.kubemq.sdk.burnin.patterns

import io.kubemq.sdk.burnin.PatternConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.cq.CQClient
import io.kubemq.sdk.cq.queryMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class QueriesPattern(
    brokerAddress: String,
    config: PatternConfig,
) : BasePattern("queries", brokerAddress, config) {

    private var sender: CQClient? = null
    private var responder: CQClient? = null

    override suspend fun createChannels() {
        val client = createClient("send")
        sender = client
        for (ch in channelNames) {
            client.createQueriesChannel(ch)
        }
    }

    override suspend fun deleteChannels() {
        val client = sender ?: createClient("cleanup")
        for (ch in channelNames) {
            try {
                client.deleteQueriesChannel(ch)
            } catch (e: Exception) {
                logger.debug("Delete channel {} failed: {}", ch, e.message)
            }
        }
    }

    override suspend fun run() {
        val scope = scope ?: return

        // Start responders
        responder = createClient("resp")
        for (ch in channelNames) {
            scope.launch {
                try {
                    responder!!.subscribeToQueries {
                        channel = ch
                        group = "burnin"
                    }.collect { query ->
                        val response = query.respond {
                            executed = true
                            metadata = "burnin-response"
                            body = "response-data".toByteArray()
                        }
                        responder!!.sendQueryResponse(response)
                        recordReceived(ch)
                    }
                } catch (_: CancellationException) {
                    // normal
                } catch (e: Exception) {
                    recordError(ch)
                    logger.debug("Responder error on {}: {}", ch, e.message)
                }
            }
        }

        // Start senders
        for (ch in channelNames) {
            scope.launch {
                rateLimitedLoop(this, config.rate) {
                    try {
                        val startTime = System.currentTimeMillis()
                        val response = sender!!.sendQuery(queryMessage {
                            channel = ch
                            metadata = "burnin"
                            body = generatePayload()
                            timeoutMs = 10_000
                        })
                        val latency = System.currentTimeMillis() - startTime
                        if (response.executed) {
                            recordSent(ch)
                            recordLatency(ch, latency)
                        } else {
                            recordError(ch)
                        }
                    } catch (_: CancellationException) {
                        throw CancellationException()
                    } catch (e: Exception) {
                        recordError(ch)
                        logger.debug("Send query error on {}: {}", ch, e.message)
                    }
                }
            }
        }
    }

    override suspend fun closeClients() {
        responder?.close()
        sender?.close()
        responder = null
        sender = null
    }

    private fun createClient(suffix: String): CQClient = KubeMQClient.cq {
        address = brokerAddress
        clientId = "burnin-queries-$suffix"
    }

    private fun generatePayload(): ByteArray = ByteArray(256) { it.toByte() }
}
