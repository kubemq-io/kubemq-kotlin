package io.kubemq.sdk.pubsub

import io.kubemq.sdk.client.ClientConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ChannelInfo
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.toKubeMQException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Client for publishing and subscribing to KubeMQ events and events store channels.
 *
 * This client is safe for concurrent use from multiple coroutines. Subscription
 * [Flow]s support structured concurrency -- cancelling the collecting coroutine
 * automatically cleans up the underlying gRPC stream.
 *
 * Create via the factory method:
 * ```kotlin
 * val pubsub = KubeMQClient.pubSub {
 *     address = "localhost:50000"
 *     clientId = "my-publisher"
 * }
 * ```
 *
 * @see KubeMQClient.pubSub
 * @see EventMessage
 * @see EventStoreMessage
 */
public class PubSubClient internal constructor(config: ClientConfig) : KubeMQClient(config) {

    private val eventStreamHelper = EventStreamHelper()

    // --- Events ---

    /**
     * Publishes a fire-and-forget event to the specified channel.
     *
     * Events are delivered to all active subscribers but are not persisted.
     * Wildcards are supported in the channel name.
     *
     * @param event The event message to publish
     * @throws KubeMQException.Validation if channel is blank or body and metadata are both empty
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun publishEvent(event: EventMessage) {
        ensureConnected()
        Validation.validateChannel(event.channel, "publishEvent")
        Validation.validateBodyOrMetadata(event.metadata, event.body, "publishEvent")
        try {
            eventStreamHelper.sendEvent(this, event.toProto(resolvedClientId, store = false))
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("publishEvent", event.channel)
        }
    }

    /**
     * Publishes a stream of events and returns a [Flow] of send results.
     *
     * Uses a bidirectional gRPC stream for high-throughput publishing.
     * The returned [Flow] emits an [EventSendResult] for each event that
     * failed to send (successful fire-and-forget events produce no result).
     *
     * @param events Flow of [EventMessage] to publish
     * @return Flow of [EventSendResult] for failed sends
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public fun publishEventStream(events: Flow<EventMessage>): Flow<EventSendResult> {
        return callbackFlow {
            ensureConnected()
            val collectJob = launch {                                              // H-1: use ProducerScope launch
                try {
                    events.collect { event ->
                        Validation.validateChannel(event.channel, "publishEventStream")
                        Validation.validateBodyOrMetadata(                          // M-19
                            event.metadata, event.body, "publishEventStream",
                        )
                        try {
                            eventStreamHelper.sendEvent(
                                this@PubSubClient,
                                event.toProto(resolvedClientId, store = false),
                            )
                        } catch (e: io.grpc.StatusRuntimeException) {
                            send(
                                EventSendResult(
                                    id = event.id,
                                    sent = false,
                                    error = e.status.description ?: "Stream error",
                                ),
                            )
                        }
                    }
                } catch (e: io.grpc.StatusRuntimeException) {                      // M-9
                    close(e.toKubeMQException("publishEventStream"))
                }
            }
            awaitClose { collectJob.cancel() }
        }
    }

    /**
     * Subscribes to events on the specified channel and returns a [Flow] of received events.
     *
     * The returned [Flow] supports structured concurrency: cancelling the collecting
     * coroutine cancels the underlying gRPC stream. Wildcards are supported in the
     * channel name (e.g., `"events.*"`).
     *
     * @param config Configuration block for [EventsSubscriptionConfig] (channel, group)
     * @return Cold [Flow] of [EventMessageReceived] -- collection starts the subscription
     * @throws KubeMQException.Validation if channel is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public fun subscribeToEvents(
        config: EventsSubscriptionConfig.() -> Unit,
    ): Flow<EventMessageReceived> {
        val subConfig = EventsSubscriptionConfig().apply(config)
        Validation.validateChannel(subConfig.channel, "subscribeToEvents")

        return callbackFlow {
            ensureConnected()
            val subscribe = kubemq.Kubemq.Subscribe.newBuilder()
                .setSubscribeTypeData(kubemq.Kubemq.Subscribe.SubscribeType.Events)
                .setClientID(resolvedClientId)
                .setChannel(subConfig.channel)
                .setGroup(subConfig.group)
                .build()

            val responseFlow = requireTransport().getStreamingStub()               // M-11
                .subscribeToEvents(subscribe)
            val collectJob = launch {                                              // H-1: ProducerScope launch
                try {
                    responseFlow.collect { eventReceive ->
                        send(EventMessageReceived.decode(eventReceive))
                    }
                    channel.close()                                                // stream completed normally
                } catch (e: io.grpc.StatusRuntimeException) {                      // M-9
                    close(e.toKubeMQException("subscribeToEvents", subConfig.channel))
                }
            }

            awaitClose { collectJob.cancel() }
        }
    }

    // --- Events Store ---

    /**
     * Publishes a persistent event to an events store channel.
     *
     * Events store messages are persisted by the broker and can be replayed
     * by subscribers using various [StartPosition] options. Wildcards are
     * NOT supported for events store channels.
     *
     * @param event The event store message to publish
     * @return [EventSendResult] confirming the send status
     * @throws KubeMQException.Validation if channel is blank, contains wildcards, or body and metadata are both empty
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun publishEventStore(event: EventStoreMessage): EventSendResult {
        ensureConnected()
        Validation.validateChannelNoWildcard(event.channel, "publishEventStore")
        Validation.validateBodyOrMetadata(event.metadata, event.body, "publishEventStore")
        try {
            return eventStreamHelper.sendEventStore(this, event.toProto(resolvedClientId))
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("publishEventStore", event.channel)
        }
    }

    /**
     * Publishes a stream of events store messages and returns a [Flow] of send results.
     *
     * Each event is sent sequentially. The returned [Flow] emits one [EventSendResult]
     * per input event, confirming persistence.
     *
     * @param events Flow of [EventStoreMessage] to publish
     * @return Flow of [EventSendResult] with send confirmation for each event
     * @throws KubeMQException.Validation if any channel is blank or contains wildcards
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public fun publishEventStoreStream(events: Flow<EventStoreMessage>): Flow<EventSendResult> {
        return events.map { event ->
            ensureConnected()
            Validation.validateChannelNoWildcard(event.channel, "publishEventStoreStream")
            Validation.validateBodyOrMetadata(event.metadata, event.body, "publishEventStoreStream")
            try {
                eventStreamHelper.sendEventStore(this, event.toProto(resolvedClientId))
            } catch (e: io.grpc.StatusRuntimeException) {
                throw e.toKubeMQException("publishEventStoreStream", event.channel)
            }
        }
    }

    /**
     * Subscribes to an events store channel and returns a [Flow] of received events.
     *
     * Supports replay from various positions via [EventsStoreSubscriptionConfig.startPosition].
     * The returned [Flow] supports structured concurrency: cancelling the collecting
     * coroutine cancels the underlying gRPC stream. Wildcards are NOT supported.
     *
     * @param config Configuration block for [EventsStoreSubscriptionConfig]
     * @return Cold [Flow] of [EventMessageReceived] -- collection starts the subscription
     * @throws KubeMQException.Validation if channel is blank or contains wildcards
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     * @throws IllegalArgumentException if [StartPosition] values are invalid (e.g., sequence <= 0)
     */
    public fun subscribeToEventsStore(
        config: EventsStoreSubscriptionConfig.() -> Unit,
    ): Flow<EventMessageReceived> {
        val subConfig = EventsStoreSubscriptionConfig().apply(config)
        Validation.validateChannelNoWildcard(subConfig.channel, "subscribeToEventsStore")
        val startPosition = subConfig.startPosition
        require(startPosition !is StartPosition.StartAtSequence || startPosition.sequence > 0) {
            "StartAtSequence value must be > 0"
        }
        require(startPosition !is StartPosition.StartAtTime || startPosition.timestampNanos > 0) {
            "StartAtTime value must be > 0"
        }
        require(startPosition !is StartPosition.StartAtTimeDelta || startPosition.seconds > 0) {
            "StartAtTimeDelta value must be > 0"
        }

        return callbackFlow {
            ensureConnected()
            val subscribeBuilder = kubemq.Kubemq.Subscribe.newBuilder()
                .setSubscribeTypeData(kubemq.Kubemq.Subscribe.SubscribeType.EventsStore)
                .setClientID(resolvedClientId)
                .setChannel(subConfig.channel)
                .setGroup(subConfig.group)

            when (startPosition) {
                is StartPosition.StartNewOnly ->
                    subscribeBuilder.setEventsStoreTypeData(
                        kubemq.Kubemq.Subscribe.EventsStoreType.StartNewOnly,
                    )
                is StartPosition.StartFromFirst ->
                    subscribeBuilder.setEventsStoreTypeData(
                        kubemq.Kubemq.Subscribe.EventsStoreType.StartFromFirst,
                    )
                is StartPosition.StartFromLast ->
                    subscribeBuilder.setEventsStoreTypeData(
                        kubemq.Kubemq.Subscribe.EventsStoreType.StartFromLast,
                    )
                is StartPosition.StartAtSequence -> {
                    subscribeBuilder.setEventsStoreTypeData(
                        kubemq.Kubemq.Subscribe.EventsStoreType.StartAtSequence,
                    )
                    subscribeBuilder.setEventsStoreTypeValue(startPosition.sequence)
                }
                is StartPosition.StartAtTime -> {
                    subscribeBuilder.setEventsStoreTypeData(
                        kubemq.Kubemq.Subscribe.EventsStoreType.StartAtTime,
                    )
                    subscribeBuilder.setEventsStoreTypeValue(startPosition.timestampNanos)
                }
                is StartPosition.StartAtTimeDelta -> {
                    subscribeBuilder.setEventsStoreTypeData(
                        kubemq.Kubemq.Subscribe.EventsStoreType.StartAtTimeDelta,
                    )
                    subscribeBuilder.setEventsStoreTypeValue(startPosition.seconds)
                }
            }

            val responseFlow = requireTransport().getStreamingStub()               // M-11
                .subscribeToEvents(subscribeBuilder.build())
            val collectJob = launch {                                              // H-1: ProducerScope launch
                try {
                    responseFlow.collect { eventReceive ->
                        send(EventMessageReceived.decode(eventReceive))
                    }
                    channel.close()                                                // stream completed normally
                } catch (e: io.grpc.StatusRuntimeException) {                      // M-9
                    close(e.toKubeMQException("subscribeToEventsStore", subConfig.channel))
                }
            }
            awaitClose { collectJob.cancel() }
        }
    }

    // --- Channel Management ---

    /**
     * Creates an events channel on the broker.
     *
     * @param channel The channel name to create
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun createEventsChannel(channel: String) {
        createChannel(channel, "events")
    }

    /**
     * Creates an events store channel on the broker.
     *
     * @param channel The channel name to create
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun createEventsStoreChannel(channel: String) {
        createChannel(channel, "events_store")
    }

    /**
     * Deletes an events channel from the broker.
     *
     * @param channel The channel name to delete
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun deleteEventsChannel(channel: String) {
        deleteChannel(channel, "events")
    }

    /**
     * Deletes an events store channel from the broker.
     *
     * @param channel The channel name to delete
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun deleteEventsStoreChannel(channel: String) {
        deleteChannel(channel, "events_store")
    }

    /**
     * Lists events channels, optionally filtered by a search string.
     *
     * @param search Optional filter string for channel names. Default: `""` (all channels).
     * @return List of [ChannelInfo] matching the search criteria
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun listEventsChannels(search: String = ""): List<ChannelInfo> =
        listChannels("events", search)

    /**
     * Lists events store channels, optionally filtered by a search string.
     *
     * @param search Optional filter string for channel names. Default: `""` (all channels).
     * @return List of [ChannelInfo] matching the search criteria
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun listEventsStoreChannels(search: String = ""): List<ChannelInfo> =
        listChannels("events_store", search)

    /**
     * Closes this client, releasing the event stream helper and the underlying gRPC channel.
     *
     * After calling this method the client cannot be reused. Any in-flight streams
     * or subscriptions will be terminated.
     */
    override fun close() {
        eventStreamHelper.close()
        super.close()
    }
}
