package io.kubemq.sdk.cq

import java.time.Instant

/**
 * Response received after sending a query.
 *
 * @see CQClient.sendQuery
 */
public class QueryResponse internal constructor(
    /** Request identifier matching the sent query. */
    public val requestId: String,
    /** `true` if the query was successfully executed by the responder. */
    public val executed: Boolean,
    /** Error message from the responder; empty if successful. */
    public val error: String,
    /** Metadata string from the responder. */
    public val metadata: String,
    /** Response payload as a byte array. */
    public val body: ByteArray,
    /** `true` if this response was served from the broker cache. */
    public val cacheHit: Boolean,
    /** Server timestamp of the response. */
    public val timestamp: Instant,
    /** Key-value tags from the responder. */
    public val tags: Map<String, String>,
) {
    internal companion object {
        fun decode(response: kubemq.Kubemq.Response): QueryResponse = QueryResponse(
            requestId = response.requestID,
            executed = response.executed,
            error = response.error,
            metadata = response.metadata,
            body = response.body.toByteArray(),
            cacheHit = response.cacheHit,
            timestamp = Instant.ofEpochSecond(response.timestamp / 1_000_000_000L, response.timestamp % 1_000_000_000L),
            tags = response.tagsMap,
        )
    }

    override fun toString(): String =
        "QueryResponse(requestId=$requestId, executed=$executed, error=$error, metadata=$metadata, bodySize=${body.size}, cacheHit=$cacheHit, timestamp=$timestamp, tags=$tags)"
}
