package io.kubemq.sdk.cq

import com.google.protobuf.ByteString
import io.kubemq.sdk.client.KubeMQDsl
import io.kubemq.sdk.common.Validation
import java.util.UUID

/**
 * A command message for the request-response (command) pattern.
 *
 * Commands are fire-and-execute: the sender waits for a boolean [CommandResponse]
 * indicating whether the command was executed successfully.
 *
 * Create instances using the [commandMessage] DSL builder:
 * ```kotlin
 * val cmd = commandMessage {
 *     channel = "commands.shutdown"
 *     metadata = "graceful"
 *     timeoutMs = 10_000
 * }
 * ```
 *
 * @see commandMessage
 * @see CQClient.sendCommand
 */
public data class CommandMessage(
    /** Unique message identifier. Auto-generated UUID if blank. */
    val id: String = "",
    /** Target channel name. Wildcards are NOT supported. Required. */
    val channel: String = "",
    /** Optional metadata string. */
    val metadata: String = "",
    /** Message payload as a byte array. */
    val body: ByteArray = ByteArray(0),
    /** Optional key-value tags. */
    val tags: Map<String, String> = emptyMap(),
    /** Timeout in milliseconds for the command response. Must be > 0. Default: `60000`. */
    val timeoutMs: Int = 60000,                                                    // H-10
    /** Optional OpenTelemetry span context for distributed tracing. */
    val span: ByteArray? = null,
) {
    internal fun validate() {
        Validation.validateChannelNoWildcard(channel, "sendCommand")               // M-20
        Validation.validatePositive(timeoutMs, "timeoutMs", "sendCommand")
    }

    internal fun toProto(clientId: String): kubemq.Kubemq.Request {
        val resolvedId = id.ifBlank { UUID.randomUUID().toString() }
        return kubemq.Kubemq.Request.newBuilder()
            .setRequestID(resolvedId)
            .setClientID(clientId)
            .setChannel(channel)
            .setRequestTypeData(kubemq.Kubemq.Request.RequestType.Command)
            .setRequestTypeDataValue(kubemq.Kubemq.Request.RequestType.Command_VALUE)
            .setMetadata(metadata)
            .setBody(ByteString.copyFrom(body))
            .putAllTags(tags)                                                      // M-15
            .putTags("x-kubemq-client-id", clientId)                               // M-15
            .setTimeout(timeoutMs)
            .also { builder ->                                                     // M-22
                span?.takeIf { it.isNotEmpty() }?.let { builder.setSpan(ByteString.copyFrom(it)) }
            }
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommandMessage) return false
        return id == other.id && channel == other.channel && metadata == other.metadata &&
            body.contentEquals(other.body) && tags == other.tags &&
            timeoutMs == other.timeoutMs && (span?.contentEquals(other.span ?: ByteArray(0)) ?: (other.span == null))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + channel.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    override fun toString(): String =
        "CommandMessage(id=$id, channel=$channel, metadata=$metadata, bodySize=${body.size}, tags=$tags, timeoutMs=$timeoutMs)"
}

/**
 * DSL builder for [CommandMessage].
 *
 * @see commandMessage
 */
@KubeMQDsl
public class CommandMessageBuilder {
    /** Unique message identifier. Leave blank for auto-generated UUID. */
    public var id: String = ""
    /** Target channel name. Required. */
    public var channel: String = ""
    /** Optional metadata string. */
    public var metadata: String = ""
    /** Message payload as a byte array. Default: empty. */
    public var body: ByteArray = ByteArray(0)
    /** Optional key-value tags. */
    public var tags: MutableMap<String, String> = mutableMapOf()
    /** Timeout in milliseconds for the command response. Must be > 0. Default: `60000`. */
    public var timeoutMs: Int = 60000
    /** Optional OpenTelemetry span context for distributed tracing. */
    public var span: ByteArray? = null

    public fun build(): CommandMessage = CommandMessage(
        id = id,
        channel = channel,
        metadata = metadata,
        body = body,
        tags = tags.toMap(),
        timeoutMs = timeoutMs,
        span = span,
    )
}

/**
 * Creates a [CommandMessage] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val cmd = commandMessage {
 *     channel = "commands.shutdown"
 *     timeoutMs = 10_000
 * }
 * ```
 *
 * @param init Configuration block for [CommandMessageBuilder]
 * @return Configured [CommandMessage] instance
 */
public inline fun commandMessage(init: CommandMessageBuilder.() -> Unit): CommandMessage =
    CommandMessageBuilder().apply(init).build()
