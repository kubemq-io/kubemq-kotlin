package io.kubemq.sdk.cq

import com.google.protobuf.ByteString
import io.kubemq.sdk.exception.KubeMQException
import java.time.Instant

/**
 * A response message to be sent back to a query sender.
 *
 * Built via [QueryReceived.respond] and sent via [CQClient.sendQueryResponse].
 *
 * @see QueryReceived.respond
 * @see CQClient.sendQueryResponse
 */
public class QueryResponseMessage internal constructor(
    /** Request identifier of the original query. */
    public val requestId: String,
    /** Reply channel (set automatically from the received query). */
    public val replyChannel: String,
    internal var clientId: String,
    /** `true` if the query was executed successfully. */
    public val executed: Boolean,
    /** Optional metadata string in the response. */
    public val metadata: String,
    /** Response payload as a byte array. */
    public val body: ByteArray,
    /** Error message if the query failed; empty string otherwise. */
    public val error: String,
    /** Optional key-value tags in the response. */
    public val tags: Map<String, String>,
    /** Optional OpenTelemetry span context for distributed tracing. */
    public val span: ByteArray? = null,                                            // M-22
) {
    internal fun validate() {
        if (replyChannel.isBlank()) {
            throw KubeMQException.Validation(
                "Query response must have a reply channel",
                operation = "sendQueryResponse",
            )
        }
    }

    internal fun toProto(clientId: String): kubemq.Kubemq.Response =
        kubemq.Kubemq.Response.newBuilder()
            .setClientID(clientId)
            .setRequestID(requestId)
            .setReplyChannel(replyChannel)
            .setExecuted(executed)
            .setError(error)
            .setTimestamp(Instant.now().epochSecond * 1_000_000_000L)
            .setMetadata(metadata)
            .setBody(ByteString.copyFrom(body))
            .putAllTags(tags)
            .also { builder ->
                span?.takeIf { it.isNotEmpty() }?.let { builder.setSpan(ByteString.copyFrom(it)) }
            }
            .build()

    override fun toString(): String =
        "QueryResponseMessage(requestId=$requestId, executed=$executed, error=$error, metadata=$metadata, bodySize=${body.size})"
}
