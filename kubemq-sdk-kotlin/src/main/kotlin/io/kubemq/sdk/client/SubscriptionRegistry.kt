package io.kubemq.sdk.client

import io.kubemq.sdk.cq.CommandsSubscriptionConfig
import io.kubemq.sdk.cq.QueriesSubscriptionConfig
import io.kubemq.sdk.pubsub.EventsStoreSubscriptionConfig
import io.kubemq.sdk.pubsub.EventsSubscriptionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Each SubscriptionEntry holds a `resubscribe` lambda of type
 * `suspend (KubeMQClient) -> Unit`. This lambda re-establishes the gRPC stream
 * and wires incoming events into the existing SharedFlow/channel that the
 * user is already collecting from. Because the lambda is `suspend` and performs
 * the full stream setup + collection internally, there is no cold Flow return
 * value that could be accidentally discarded.
 */
internal sealed class SubscriptionEntry {
    abstract val id: String
    /** Re-establishes the gRPC stream and bridges events to the original channel/SharedFlow. */
    abstract val resubscribe: suspend (KubeMQClient) -> Unit

    data class EventsSub(
        override val id: String,
        val config: EventsSubscriptionConfig,
        override val resubscribe: suspend (KubeMQClient) -> Unit,
    ) : SubscriptionEntry()

    data class EventsStoreSub(
        override val id: String,
        val config: EventsStoreSubscriptionConfig,
        override val resubscribe: suspend (KubeMQClient) -> Unit,
    ) : SubscriptionEntry()

    data class CommandsSub(
        override val id: String,
        val config: CommandsSubscriptionConfig,
        override val resubscribe: suspend (KubeMQClient) -> Unit,
    ) : SubscriptionEntry()

    data class QueriesSub(
        override val id: String,
        val config: QueriesSubscriptionConfig,
        override val resubscribe: suspend (KubeMQClient) -> Unit,
    ) : SubscriptionEntry()
}

internal class SubscriptionRegistry {
    private val entries = ConcurrentHashMap<String, SubscriptionEntry>()

    fun register(entry: SubscriptionEntry) {
        entries[entry.id] = entry
    }

    fun unregister(id: String) {
        entries.remove(id)
    }

    /**
     * Re-subscribes all active subscriptions after reconnection.
     *
     * Each entry's `resubscribe` lambda is a `suspend` function that internally
     * re-establishes the gRPC stream and collects it, bridging events into the
     * original ProducerScope/channel. A new coroutine is launched per subscription
     * in the client's scope so that collection runs concurrently and the caller
     * (onSuccess callback) is not blocked.
     */
    fun resubscribeAll(client: KubeMQClient, scope: CoroutineScope) {
        if (entries.isEmpty()) return
        client.logger.info { "Re-subscribing ${entries.size} active subscriptions" }
        for ((_, entry) in entries) {
            scope.launch {
                try {
                    entry.resubscribe(client)
                } catch (e: Exception) {
                    client.logger.warn { "Failed to re-subscribe ${entry.id}: ${e.message}" }
                }
            }
        }
    }

    fun isEmpty(): Boolean = entries.isEmpty()
}
