package io.kubemq.sdk.queues

import java.time.Instant

/**
 * Server-assigned attributes for a received queue message.
 *
 * @see QueueReceivedMessage.attributes
 */
public data class QueueMessageAttributes(
    /** Timestamp when the message was originally published. */
    public val timestamp: Instant,
    /** Server-assigned sequence number. */
    public val sequence: Long,
    /** Number of times this message has been received (delivery attempts). */
    public val receiveCount: Int,
    /** `true` if this message was re-routed from another queue (e.g., dead-letter). */
    public val reRouted: Boolean,
    /** Name of the original queue if this message was re-routed; empty otherwise. */
    public val reRoutedFromQueue: String,
    /** Timestamp when this message will expire; [Instant.EPOCH] if no expiration. */
    public val expirationAt: Instant,
    /** Timestamp when this message becomes available (delayed delivery); [Instant.EPOCH] if immediate. */
    public val delayedTo: Instant,
    /** MD5 hash of the message body (if available from the broker). */
    public val md5OfBody: String = "",                                             // M-23
) {
    internal companion object {
        fun fromProto(attrs: kubemq.Kubemq.QueueMessageAttributes): QueueMessageAttributes =
            QueueMessageAttributes(
                timestamp = Instant.ofEpochSecond(                                 // M-18
                    attrs.timestamp / 1_000_000_000L,
                    attrs.timestamp % 1_000_000_000L,
                ),
                sequence = attrs.sequence,
                receiveCount = attrs.receiveCount,
                reRouted = attrs.reRouted,
                reRoutedFromQueue = attrs.reRoutedFromQueue,
                expirationAt = Instant.ofEpochSecond(                              // M-18
                    attrs.expirationAt / 1_000_000_000L,
                    attrs.expirationAt % 1_000_000_000L,
                ),
                delayedTo = Instant.ofEpochSecond(                                 // M-18
                    attrs.delayedTo / 1_000_000_000L,
                    attrs.delayedTo % 1_000_000_000L,
                ),
                md5OfBody = attrs.mD5OfBody,                                       // M-23
            )
    }
}
