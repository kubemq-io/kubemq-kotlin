package io.kubemq.sdk.cq

/**
 * A command request received from a subscription.
 *
 * Use [respond] to build a [CommandResponseMessage] and then send it
 * via [CQClient.sendCommandResponse].
 *
 * @see CQClient.subscribeToCommands
 * @see CQClient.sendCommandResponse
 */
public class CommandReceived internal constructor(
    /** Server-assigned request identifier. */
    public val id: String,
    /** Channel the command was sent to. */
    public val channel: String,
    /** Metadata string from the sender. */
    public val metadata: String,
    /** Command payload as a byte array. */
    public val body: ByteArray,
    /** Reply channel for sending the response (used internally). */
    public val replyChannel: String,
    /** Key-value tags from the sender. */
    public val tags: Map<String, String>,
) {
    internal companion object {
        fun decode(request: kubemq.Kubemq.Request): CommandReceived = CommandReceived(
            id = request.requestID,
            channel = request.channel,
            metadata = request.metadata,
            body = request.body.toByteArray(),
            replyChannel = request.replyChannel,
            tags = HashMap(request.tagsMap),
        )
    }

    /**
     * Builds a [CommandResponseMessage] for this command.
     *
     * The [requestId] and [replyChannel] are automatically set from this received command.
     *
     * @param block Configuration block for [CommandResponseBuilder]
     * @return A [CommandResponseMessage] ready to send via [CQClient.sendCommandResponse]
     */
    public fun respond(block: CommandResponseBuilder.() -> Unit): CommandResponseMessage {
        val builder = CommandResponseBuilder().apply(block)
        return CommandResponseMessage(
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
        "CommandReceived(id=$id, channel=$channel, metadata=$metadata, bodySize=${body.size}, replyChannel=$replyChannel, tags=$tags)"
}
