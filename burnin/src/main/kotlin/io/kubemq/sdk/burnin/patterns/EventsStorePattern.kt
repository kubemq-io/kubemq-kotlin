package io.kubemq.sdk.burnin.patterns

import io.kubemq.sdk.burnin.PatternConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.PubSubClient
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EventsStorePattern(
    brokerAddress: String,
    config: PatternConfig,
) : BasePattern("events_store", brokerAddress, config) {

    private var publisher: PubSubClient? = null
    private var subscriber: PubSubClient? = null

    override suspend fun createChannels() {
        val client = createClient("pub")
        publisher = client
        for (ch in channelNames) {
            client.createEventsStoreChannel(ch)
        }
    }

    override suspend fun deleteChannels() {
        val client = publisher ?: createClient("cleanup")
        for (ch in channelNames) {
            try {
                client.deleteEventsStoreChannel(ch)
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
                    subscriber!!.subscribeToEventsStore {
                        channel = ch
                        group = "burnin"
                        startPosition = StartPosition.StartNewOnly
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
                        publisher!!.publishEventStore(eventStoreMessage {
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
        clientId = "burnin-events-store-$suffix"
    }

    private fun generatePayload(): ByteArray = ByteArray(256) { it.toByte() }
}
