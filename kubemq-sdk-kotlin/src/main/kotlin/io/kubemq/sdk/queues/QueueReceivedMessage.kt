package io.kubemq.sdk.queues

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A message received from a queue channel.
 *
 * For messages received with `autoAck = false` (stream API), you must call exactly one of
 * [ack], [reject], or [reQueue] to complete the transaction. These methods are thread-safe
 * and protected by an [AtomicBoolean] guard --
 * calling any of them more than once throws [IllegalStateException].
 *
 * Messages received via the simple API are auto-acknowledged and do not support
 * ack/reject/reQueue operations.
 *
 * @see QueuesClient.receiveQueuesMessages
 * @see QueuesClient.receiveQueueMessages
 */
public class QueueReceivedMessage internal constructor(
    /** Server-assigned message identifier. */
    public val id: String,
    /** Queue channel the message was received from. */
    public val channel: String,
    /** Metadata string from the sender. */
    public val metadata: String,
    /** Message payload as a byte array. */
    public val body: ByteArray,
    /** Client ID of the sender. */
    public val fromClientId: String,
    /** Key-value tags from the sender. */
    public val tags: Map<String, String>,
    /** Message delivery attributes (timestamps, sequence, receive count, routing info). */
    public val attributes: QueueMessageAttributes,
    internal val transactionId: String,
    internal val receiverClientId: String,
    internal val sequence: Long,
    internal val isAutoAcked: Boolean,
    internal val completed: AtomicBoolean = AtomicBoolean(false),                      // M-6
    internal val handler: QueueDownstreamHandler?,
) {
    /**
     * Acknowledges this message, removing it from the queue.
     *
     * Thread-safe: protected by an [AtomicBoolean] guard. Calling this more than once
     * throws [IllegalStateException].
     *
     * @throws IllegalStateException if the message was auto-acked, handler is not set, or transaction already completed
     */
    public fun ack() {
        check(!isAutoAcked) { "Auto-acked message, operations are not allowed" }
        checkNotNull(handler) { "Response handler not set" }
        check(completed.compareAndSet(false, true)) { "Transaction already completed" } // M-6
        handler.sendAck(transactionId, receiverClientId, sequence)
    }

    /**
     * Rejects this message, returning it to the queue for re-delivery.
     *
     * Thread-safe: protected by an [AtomicBoolean] guard. Calling this more than once
     * throws [IllegalStateException].
     *
     * @throws IllegalStateException if the message was auto-acked, handler is not set, or transaction already completed
     */
    public fun reject() {
        check(!isAutoAcked) { "Auto-acked message, operations are not allowed" }
        checkNotNull(handler) { "Response handler not set" }
        check(completed.compareAndSet(false, true)) { "Transaction already completed" } // M-6
        handler.sendReject(transactionId, receiverClientId, sequence)
    }

    /**
     * Re-queues this message to a different channel.
     *
     * Thread-safe: protected by an [AtomicBoolean] guard. Calling this more than once
     * throws [IllegalStateException].
     *
     * @param channel The target queue channel for re-queuing (must not be blank)
     * @throws IllegalArgumentException if [channel] is blank
     * @throws IllegalStateException if the message was auto-acked, handler is not set, or transaction already completed
     */
    public fun reQueue(channel: String) {
        require(channel.isNotBlank()) { "Re-queue channel cannot be empty" }
        check(!isAutoAcked) { "Auto-acked message, operations are not allowed" }
        checkNotNull(handler) { "Response handler not set" }
        check(completed.compareAndSet(false, true)) { "Transaction already completed" } // M-6
        handler.sendReQueue(transactionId, receiverClientId, sequence, channel)
    }

    internal companion object {
        private val DEFAULT_ATTRIBUTES = QueueMessageAttributes(
            timestamp = java.time.Instant.EPOCH,
            sequence = 0L,
            receiveCount = 0,
            reRouted = false,
            reRoutedFromQueue = "",
            expirationAt = java.time.Instant.EPOCH,
            delayedTo = java.time.Instant.EPOCH,
        )

        private fun parseAttributes(proto: kubemq.Kubemq.QueueMessage): QueueMessageAttributes =
            if (proto.hasAttributes()) QueueMessageAttributes.fromProto(proto.attributes)
            else DEFAULT_ATTRIBUTES

        fun fromProto(
            proto: kubemq.Kubemq.QueueMessage,
            transactionId: String,
            receiverClientId: String,
            isAutoAcked: Boolean,
            handler: QueueDownstreamHandler?,
        ): QueueReceivedMessage {
            val attrs = parseAttributes(proto)
            return QueueReceivedMessage(
                id = proto.messageID,
                channel = proto.channel,
                metadata = proto.metadata,
                body = proto.body.toByteArray(),
                fromClientId = proto.clientID,
                tags = proto.tagsMap.toMap(),
                attributes = attrs,
                transactionId = transactionId,
                receiverClientId = receiverClientId,
                sequence = attrs.sequence,
                isAutoAcked = isAutoAcked,
                handler = handler,
            )
        }

        fun fromSimpleProto(
            proto: kubemq.Kubemq.QueueMessage,
            clientId: String,
        ): QueueReceivedMessage {
            val attrs = parseAttributes(proto)
            return QueueReceivedMessage(
                id = proto.messageID,
                channel = proto.channel,
                metadata = proto.metadata,
                body = proto.body.toByteArray(),
                fromClientId = proto.clientID,
                tags = proto.tagsMap.toMap(),
                attributes = attrs,
                transactionId = "",
                receiverClientId = clientId,
                sequence = attrs.sequence,
                isAutoAcked = true,
                handler = null,
            )
        }
    }

    override fun toString(): String =
        "QueueReceivedMessage(id='$id', channel='$channel', metadata='$metadata', " +
            "body=${body.size} bytes, tags=$tags)"
}
