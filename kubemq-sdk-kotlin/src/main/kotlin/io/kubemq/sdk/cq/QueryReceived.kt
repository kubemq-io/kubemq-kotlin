package io.kubemq.sdk.cq

/**
 * A query request received from a subscription.
 *
 * Use [respond] to build a [QueryResponseMessage] and then send it
 * via [CQClient.sendQueryResponse].
 *
 * @see CQClient.subscribeToQueries
 * @see CQClient.sendQueryResponse
 */
public class QueryReceived internal constructor(
    /** Server-assigned request identifier. */
    public val id: String,
    /** Channel the query was sent to. */
    public val channel: String,
    /** Metadata string from the sender. */
    public val metadata: String,
    /** Query payload as a byte array. */
    public val body: ByteArray,
    /** Reply channel for sending the response (used internally). */
    public val replyChannel: String,
    /** Key-value tags from the sender. */
    public val tags: Map<String, String>,
) {
    internal companion object {
        fun decode(request: kubemq.Kubemq.Request): QueryReceived = QueryReceived(
            id = request.requestID,
            channel = request.channel,
            metadata = request.metadata,
            body = request.body.toByteArray(),
            replyChannel = request.replyChannel,
            tags = HashMap(request.tagsMap),
        )
    }

    /**
     * Builds a [QueryResponseMessage] for this query.
     *
     * The [requestId] and [replyChannel] are automatically set from this received query.
     *
     * @param block Configuration block for [QueryResponseBuilder]
     * @return A [QueryResponseMessage] ready to send via [CQClient.sendQueryResponse]
     */
    public fun respond(block: QueryResponseBuilder.() -> Unit): QueryResponseMessage {
        val builder = QueryResponseBuilder().apply(block)
        return QueryResponseMessage(
            requestId = id,
            replyChannel = replyChannel,
            clientId = "",
            executed = builder.executed,
            metadata = builder.metadata,
            body = builder.body,
            error = builder.error,
            tags = builder.tags,
        )
    }

    override fun toString(): String =
        "QueryReceived(id=$id, channel=$channel, metadata=$metadata, bodySize=${body.size}, replyChannel=$replyChannel, tags=$tags)"
}
