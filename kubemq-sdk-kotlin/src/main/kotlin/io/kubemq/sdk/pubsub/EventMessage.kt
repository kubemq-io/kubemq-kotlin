package io.kubemq.sdk.pubsub

import com.google.protobuf.ByteString
import io.kubemq.sdk.client.KubeMQDsl
import java.util.UUID

/**
 * A fire-and-forget event message for publishing to an events channel.
 *
 * Create instances using the [eventMessage] DSL builder:
 * ```kotlin
 * val msg = eventMessage {
 *     channel = "events.temperature"
 *     metadata = "sensor-1"
 *     body = """{"temp": 23.5}""".toByteArray()
 *     tags = mapOf("source" to "iot")
 * }
 * ```
 *
 * @see eventMessage
 * @see PubSubClient.publishEvent
 */
public class EventMessage internal constructor(
    /** Unique message identifier. Auto-generated UUID if left blank. */
    public val id: String,
    /** Target channel name. Wildcards are supported for events (not events store). */
    public val channel: String,
    /** Optional metadata string associated with the event. */
    public val metadata: String,
    /** Message payload as a byte array. */
    public val body: ByteArray,
    /** Optional key-value tags for filtering or routing. */
    public val tags: Map<String, String>,
)

/**
 * Creates an [EventMessage] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val msg = eventMessage {
 *     channel = "events.temperature"
 *     body = "hello".toByteArray()
 * }
 * ```
 *
 * @param block Configuration block for [EventMessageBuilder]
 * @return Configured [EventMessage] instance
 */
public fun eventMessage(block: EventMessageBuilder.() -> Unit): EventMessage =
    EventMessageBuilder().apply(block).build()

/**
 * DSL builder for [EventMessage].
 *
 * @see eventMessage
 */
@KubeMQDsl
public class EventMessageBuilder {
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

    internal fun build(): EventMessage = EventMessage(
        id = id.ifBlank { UUID.randomUUID().toString() },
        channel = channel,
        metadata = metadata,
        body = body,
        tags = tags,
    )
}

internal fun EventMessage.toProto(clientId: String, store: Boolean = false): kubemq.Kubemq.Event =
    buildEventProto(id, clientId, channel, metadata, body, tags, store)

internal fun buildEventProto(
    id: String,
    clientId: String,
    channel: String,
    metadata: String,
    body: ByteArray,
    tags: Map<String, String>,
    store: Boolean,
): kubemq.Kubemq.Event {
    return kubemq.Kubemq.Event.newBuilder()
        .setEventID(id)
        .setClientID(clientId)
        .setChannel(channel)
        .setMetadata(metadata)
        .setBody(ByteString.copyFrom(body))
        .setStore(store)
        .putAllTags(tags)
        .putTags("x-kubemq-client-id", clientId)
        .build()
}
