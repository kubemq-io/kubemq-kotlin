package io.kubemq.sdk.cq

import java.time.Instant

/**
 * Response received after sending a command.
 *
 * @see CQClient.sendCommand
 */
public class CommandResponse internal constructor(
    /** Request identifier matching the sent command. */
    public val requestId: String,
    /** `true` if the command was successfully executed by the responder. */
    public val executed: Boolean,
    /** Error message from the responder; empty if successful. */
    public val error: String,
    /** Server timestamp of the response. */
    public val timestamp: Instant,
) {
    internal companion object {
        fun decode(response: kubemq.Kubemq.Response): CommandResponse = CommandResponse(
            requestId = response.requestID,
            executed = response.executed,
            error = response.error,
            timestamp = Instant.ofEpochSecond(response.timestamp / 1_000_000_000L, response.timestamp % 1_000_000_000L),
        )
    }

    override fun toString(): String =
        "CommandResponse(requestId=$requestId, executed=$executed, error=$error, timestamp=$timestamp)"
}
