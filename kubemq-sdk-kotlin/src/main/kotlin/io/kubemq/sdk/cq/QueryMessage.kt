package io.kubemq.sdk.cq

import com.google.protobuf.ByteString
import io.kubemq.sdk.client.KubeMQDsl
import io.kubemq.sdk.common.Validation
import java.util.UUID

/**
 * A query message for the request-response (query) pattern.
 *
 * Queries expect a response with data. Responses can be cached by the broker
 * using [cacheKey] and [cacheTtlSeconds].
 *
 * Create instances using the [queryMessage] DSL builder:
 * ```kotlin
 * val query = queryMessage {
 *     channel = "queries.user-lookup"
 *     body = """{"id": 42}""".toByteArray()
 *     timeoutMs = 10_000
 *     cacheKey = "user-42"
 *     cacheTtlSeconds = 60
 * }
 * ```
 *
 * @see queryMessage
 * @see CQClient.sendQuery
 */
public data class QueryMessage(
    /** Unique message identifier. Auto-generated UUID if blank. */
    val id: String = "",
    /** Target channel name. Wildcards are NOT supported. Required. */
    val channel: String = "",
    /** Optional metadata string. */
    val metadata: String = "",
    /** Query payload as a byte array. */
    val body: ByteArray = ByteArray(0),
    /** Optional key-value tags. */
    val tags: Map<String, String> = emptyMap(),
    /** Timeout in milliseconds for the query response. Must be > 0. Default: `60000`. */
    val timeoutMs: Int = 60000,                                                    // H-10
    /** Cache key for response caching. Leave empty to disable caching. */
    val cacheKey: String = "",
    /** Cache TTL in seconds. Only applies when [cacheKey] is set. Default: `0` (no caching). */
    val cacheTtlSeconds: Int = 0,
    /** Optional OpenTelemetry span context for distributed tracing. */
    val span: ByteArray? = null,
) {
    internal fun validate() {
        Validation.validateChannelNoWildcard(channel, "sendQuery")                 // M-20
        Validation.validatePositive(timeoutMs, "timeoutMs", "sendQuery")
    }

    internal fun toProto(clientId: String): kubemq.Kubemq.Request {
        val resolvedId = id.ifBlank { UUID.randomUUID().toString() }
        return kubemq.Kubemq.Request.newBuilder()
            .setRequestID(resolvedId)
            .setClientID(clientId)
            .setChannel(channel)
            .setRequestTypeData(kubemq.Kubemq.Request.RequestType.Query)
            .setRequestTypeDataValue(kubemq.Kubemq.Request.RequestType.Query_VALUE)
            .setMetadata(metadata)
            .setBody(ByteString.copyFrom(body))
            .putAllTags(tags)                                                      // M-15
            .putTags("x-kubemq-client-id", clientId)                               // M-15
            .setTimeout(timeoutMs)
            .setCacheKey(cacheKey)
            .setCacheTTL(cacheTtlSeconds)                                          // C-3
            .also { builder ->                                                     // M-22
                span?.takeIf { it.isNotEmpty() }?.let { builder.setSpan(ByteString.copyFrom(it)) }
            }
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueryMessage) return false
        return id == other.id && channel == other.channel && metadata == other.metadata &&
            body.contentEquals(other.body) && tags == other.tags &&
            timeoutMs == other.timeoutMs && cacheKey == other.cacheKey &&
            cacheTtlSeconds == other.cacheTtlSeconds &&
            (span?.contentEquals(other.span ?: ByteArray(0)) ?: (other.span == null))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + channel.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    override fun toString(): String =
        "QueryMessage(id=$id, channel=$channel, metadata=$metadata, bodySize=${body.size}, tags=$tags, timeoutMs=$timeoutMs, cacheKey=$cacheKey, cacheTtlSeconds=$cacheTtlSeconds)"
}

/**
 * DSL builder for [QueryMessage].
 *
 * @see queryMessage
 */
@KubeMQDsl
public class QueryMessageBuilder {
    /** Unique message identifier. Leave blank for auto-generated UUID. */
    public var id: String = ""
    /** Target channel name. Required. */
    public var channel: String = ""
    /** Optional metadata string. */
    public var metadata: String = ""
    /** Query payload as a byte array. Default: empty. */
    public var body: ByteArray = ByteArray(0)
    /** Optional key-value tags. */
    public var tags: MutableMap<String, String> = mutableMapOf()
    /** Timeout in milliseconds for the query response. Must be > 0. Default: `60000`. */
    public var timeoutMs: Int = 60000
    /** Cache key for response caching. Leave empty to disable caching. */
    public var cacheKey: String = ""
    /** Cache TTL in seconds. Only applies when [cacheKey] is set. Default: `0` (no caching). */
    public var cacheTtlSeconds: Int = 0
    /** Optional OpenTelemetry span context for distributed tracing. */
    public var span: ByteArray? = null

    public fun build(): QueryMessage = QueryMessage(
        id = id,
        channel = channel,
        metadata = metadata,
        body = body,
        tags = tags.toMap(),
        timeoutMs = timeoutMs,
        cacheKey = cacheKey,
        cacheTtlSeconds = cacheTtlSeconds,
        span = span,
    )
}

/**
 * Creates a [QueryMessage] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val query = queryMessage {
 *     channel = "queries.user-lookup"
 *     body = """{"id": 42}""".toByteArray()
 *     timeoutMs = 10_000
 * }
 * ```
 *
 * @param init Configuration block for [QueryMessageBuilder]
 * @return Configured [QueryMessage] instance
 */
public inline fun queryMessage(init: QueryMessageBuilder.() -> Unit): QueryMessage =
    QueryMessageBuilder().apply(init).build()
