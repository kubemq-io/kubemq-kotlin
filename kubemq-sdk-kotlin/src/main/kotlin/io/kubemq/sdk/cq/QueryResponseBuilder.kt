package io.kubemq.sdk.cq

import io.kubemq.sdk.client.KubeMQDsl

/**
 * DSL builder for query responses, used inside [QueryReceived.respond].
 *
 * @see QueryReceived.respond
 */
@KubeMQDsl
public class QueryResponseBuilder {
    /** Whether the query was executed successfully. Default: `false`. */
    public var executed: Boolean = false

    /** Optional metadata string in the response. */
    public var metadata: String = ""

    /** Response payload as a byte array. */
    public var body: ByteArray = ByteArray(0)

    /** Error message if the query failed; leave empty for success. */
    public var error: String = ""

    /** Optional key-value tags in the response. */
    public var tags: Map<String, String> = emptyMap()
}
