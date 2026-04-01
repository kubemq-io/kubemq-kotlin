package io.kubemq.sdk.cq

import io.kubemq.sdk.client.KubeMQDsl

/**
 * DSL builder for command responses, used inside [CommandReceived.respond].
 *
 * @see CommandReceived.respond
 */
@KubeMQDsl
public class CommandResponseBuilder {
    /** Whether the command was executed successfully. Default: `false`. */
    public var executed: Boolean = false

    /** Optional metadata string in the response. */
    public var metadata: String = ""

    /** Optional response payload as a byte array. */
    public var body: ByteArray = ByteArray(0)

    /** Error message if the command failed; leave empty for success. */
    public var error: String = ""

    /** Optional key-value tags in the response. */
    public var tags: Map<String, String> = emptyMap()
}
