package io.kubemq.sdk.burnin.patterns

import io.kubemq.sdk.burnin.PatternConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.PubSubClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EventsPattern(
    brokerAddress: String,
    config: PatternConfig,
) : BasePattern("events", brokerAddress, config) {

    private var publisher: PubSubClient? = null
    private var subscriber: PubSubClient? = null

    override suspend fun createChannels() {
        val client = createClient("pub")
        publisher = client
        for (ch in channelNames) {
            client.createEventsChannel(ch)
        }
    }

    override suspend fun deleteChannels() {
        val client = publisher ?: createClient("cleanup")
        for (ch in channelNames) {
            try {
                client.deleteEventsChannel(ch)
            } catch (e: Exception) {
                logger.debug("Delete channel {} failed: {}", ch, e.message)
            }
        }
    }

    override suspend fun run() {
        val scope = scope ?: return

        // Start subscribers
        subscriber = createClient("sub")
        for (ch in channelNames) {
            scope.launch {
                try {
                    subscriber!!.subscribeToEvents {
                        channel = ch
                        group = "burnin"
                    }.collect {
                        recordReceived(ch)
                    }
                } catch (_: CancellationException) {
                    // normal
                } catch (e: Exception) {
                    recordError(ch)
                    logger.debug("Subscriber error on {}: {}", ch, e.message)
                }
            }
        }

        // Start publishers
        for (ch in channelNames) {
            scope.launch {
                rateLimitedLoop(this, config.rate) {
                    try {
                        publisher!!.publishEvent(eventMessage {
                            channel = ch
                            metadata = "burnin"
                            body = generatePayload()
                        })
                        recordSent(ch)
                    } catch (_: CancellationException) {
                        throw CancellationException()
                    } catch (e: Exception) {
                        recordError(ch)
                        logger.debug("Publish error on {}: {}", ch, e.message)
                    }
                }
            }
        }
    }

    override suspend fun closeClients() {
        subscriber?.close()
        publisher?.close()
        subscriber = null
        publisher = null
    }

    private fun createClient(suffix: String): PubSubClient = KubeMQClient.pubSub {
        address = brokerAddress
        clientId = "burnin-events-$suffix"
    }

    private fun generatePayload(): ByteArray = ByteArray(256) { it.toByte() }
}
