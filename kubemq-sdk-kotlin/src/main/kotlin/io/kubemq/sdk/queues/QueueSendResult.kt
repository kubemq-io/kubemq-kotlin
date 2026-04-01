package io.kubemq.sdk.queues

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Result of sending a message to a queue channel.
 *
 * @see QueuesClient.sendQueuesMessage
 * @see QueuesClient.sendQueueMessage
 */
public data class QueueSendResult(
    /** Message identifier matching the sent message. */
    public val messageId: String,
    /** Timestamp when the message was accepted by the broker, or `null` if sending failed. */
    public val sentAt: LocalDateTime?,
    /** Timestamp when the message will expire, or `null` if no expiration is set. */
    public val expirationAt: LocalDateTime?,
    /** Timestamp when the message will become available (delayed delivery), or `null`. */
    public val delayedTo: LocalDateTime?,
    /** `true` if the broker reported an error. */
    public val isError: Boolean,
    /** Error message from the broker; empty string if successful. */
    public val error: String,
) {
    internal companion object {
        private fun nanosToDateTime(nanos: Long): LocalDateTime? =
            if (nanos > 0) {
                LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L),
                    ZoneId.systemDefault(),
                )
            } else {
                null
            }

        fun fromProto(result: kubemq.Kubemq.SendQueueMessageResult): QueueSendResult =
            QueueSendResult(
                messageId = result.messageID,
                sentAt = nanosToDateTime(result.sentAt),
                expirationAt = nanosToDateTime(result.expirationAt),
                delayedTo = nanosToDateTime(result.delayedTo),
                isError = result.isError,
                error = result.error,
            )

        fun error(requestId: String, message: String): QueueSendResult =
            QueueSendResult(
                messageId = requestId,
                sentAt = null,
                expirationAt = null,
                delayedTo = null,
                isError = true,
                error = message,
            )
    }
}
