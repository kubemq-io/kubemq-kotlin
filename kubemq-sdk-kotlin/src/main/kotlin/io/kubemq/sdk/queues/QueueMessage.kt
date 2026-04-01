package io.kubemq.sdk.queues

import com.google.protobuf.ByteString
import io.kubemq.sdk.client.KubeMQDsl
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.KubeMQException
import java.util.UUID

/**
 * A message for sending to a KubeMQ queue channel.
 *
 * Create instances using the [queueMessage] DSL builder:
 * ```kotlin
 * val msg = queueMessage {
 *     channel = "queues.tasks"
 *     body = "process-this".toByteArray()
 *     policy = QueueMessagePolicy(
 *         expirationSeconds = 3600,
 *         maxReceiveCount = 3,
 *         maxReceiveQueue = "queues.dead-letter",
 *     )
 * }
 * ```
 *
 * @see queueMessage
 * @see QueuesClient.sendQueuesMessage
 * @see QueueMessagePolicy
 */
public data class QueueMessage(
    /** Unique message identifier. Auto-generated UUID if blank. */
    val id: String = "",
    /** Target queue channel name. Wildcards are NOT supported. Required. */
    val channel: String = "",
    /** Optional metadata string. */
    val metadata: String = "",
    /** Message payload as a byte array. */
    val body: ByteArray = ByteArray(0),
    /** Optional key-value tags. */
    val tags: Map<String, String> = emptyMap(),
    /** Optional message delivery policy (expiration, delay, dead-letter). */
    val policy: QueueMessagePolicy? = null,
) {
    internal fun validate() {
        Validation.validateChannelNoWildcard(channel, "QueueMessage.validate")     // M-20
        if (metadata.isEmpty() && body.isEmpty() && tags.isEmpty()) {
            throw KubeMQException.Validation(
                "Queue message must have at least one of: metadata, body, or tags",
                operation = "QueueMessage.validate",
                channel = channel,
            )
        }
    }

    internal fun toProto(clientId: String): kubemq.Kubemq.QueueMessage {
        val effectiveId = id.ifBlank { UUID.randomUUID().toString() }
        val builder = kubemq.Kubemq.QueueMessage.newBuilder()
            .setMessageID(effectiveId)
            .setClientID(clientId)
            .setChannel(channel)
            .setMetadata(metadata)
            .setBody(ByteString.copyFrom(body))
            .putAllTags(tags)

        val p = policy
        if (p != null) {
            builder.setPolicy(p.toProto())
        }
        return builder.build()
    }

    internal fun toUpstreamRequest(clientId: String): kubemq.Kubemq.QueuesUpstreamRequest =
        kubemq.Kubemq.QueuesUpstreamRequest.newBuilder()
            .setRequestID(UUID.randomUUID().toString())
            .addMessages(toProto(clientId))
            .build()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueueMessage) return false
        return id == other.id && channel == other.channel && metadata == other.metadata &&
            body.contentEquals(other.body) && tags == other.tags && policy == other.policy
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + channel.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    override fun toString(): String =
        "QueueMessage(id='$id', channel='$channel', metadata='$metadata', " +
            "body=${body.size} bytes, tags=$tags, policy=$policy)"
}

/**
 * DSL builder for [QueueMessage].
 *
 * @see queueMessage
 */
@KubeMQDsl
public class QueueMessageBuilder {
    /** Unique message identifier. Leave blank for auto-generated UUID. */
    public var id: String = ""
    /** Target queue channel name. Required. */
    public var channel: String = ""
    /** Optional metadata string. */
    public var metadata: String = ""
    /** Message payload as a byte array. Default: empty. */
    public var body: ByteArray = ByteArray(0)
    /** Optional key-value tags. */
    public var tags: MutableMap<String, String> = mutableMapOf()
    /** Optional message delivery policy (expiration, delay, dead-letter). */
    public var policy: QueueMessagePolicy? = null

    public fun build(): QueueMessage = QueueMessage(
        id = id,
        channel = channel,
        metadata = metadata,
        body = body,
        tags = tags.toMap(),
        policy = policy,
    )
}

/**
 * Creates a [QueueMessage] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val msg = queueMessage {
 *     channel = "queues.tasks"
 *     body = "process-this".toByteArray()
 * }
 * ```
 *
 * @param init Configuration block for [QueueMessageBuilder]
 * @return Configured [QueueMessage] instance
 */
public inline fun queueMessage(init: QueueMessageBuilder.() -> Unit): QueueMessage =
    QueueMessageBuilder().apply(init).build()
