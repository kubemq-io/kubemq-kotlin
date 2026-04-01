package io.kubemq.sdk.pubsub

import io.kubemq.sdk.client.KubeMQDsl
import java.util.UUID

/**
 * A persistent event message for publishing to an events store channel.
 *
 * Unlike [EventMessage], events store messages are persisted by the broker
 * and can be replayed by subscribers. Wildcards are NOT supported in the channel name.
 *
 * Create instances using the [eventStoreMessage] DSL builder:
 * ```kotlin
 * val msg = eventStoreMessage {
 *     channel = "events-store.orders"
 *     body = orderJson.toByteArray()
 * }
 * ```
 *
 * @see eventStoreMessage
 * @see PubSubClient.publishEventStore
 */
public class EventStoreMessage internal constructor(
    /** Unique message identifier. Auto-generated UUID if left blank. */
    public val id: String,
    /** Target channel name. Wildcards are NOT supported for events store. */
    public val channel: String,
    /** Optional metadata string associated with the event. */
    public val metadata: String,
    /** Message payload as a byte array. */
    public val body: ByteArray,
    /** Optional key-value tags for filtering or routing. */
    public val tags: Map<String, String>,
)

/**
 * Creates an [EventStoreMessage] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val msg = eventStoreMessage {
 *     channel = "events-store.orders"
 *     body = orderJson.toByteArray()
 * }
 * ```
 *
 * @param block Configuration block for [EventStoreMessageBuilder]
 * @return Configured [EventStoreMessage] instance
 */
public fun eventStoreMessage(block: EventStoreMessageBuilder.() -> Unit): EventStoreMessage =
    EventStoreMessageBuilder().apply(block).build()

/**
 * DSL builder for [EventStoreMessage].
 *
 * @see eventStoreMessage
 */
@KubeMQDsl
public class EventStoreMessageBuilder {
    /** Unique message identifier. Leave blank for auto-generated UUID. */
    public var id: String = ""
    /** Target channel name. Required. */
    public var channel: String = ""
    /** Optional metadata string. */
    public var metadata: String = ""
    /** Message payload as a byte array. Default: empty. */
    public var body: ByteArray = ByteArray(0)
    /** Optional key-value tags. Default: empty map. */
    public var tags: Map<String, String> = emptyMap()

    internal fun build(): EventStoreMessage = EventStoreMessage(
        id = id.ifBlank { UUID.randomUUID().toString() },
        channel = channel,
        metadata = metadata,
        body = body,
        tags = tags,
    )
}

internal fun EventStoreMessage.toProto(clientId: String): kubemq.Kubemq.Event =
    buildEventProto(id, clientId, channel, metadata, body, tags, store = true)
