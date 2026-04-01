package io.kubemq.sdk.queues

/**
 * Response from receiving queue messages via the stream API.
 *
 * Contains the received messages and transaction metadata for bulk operations
 * ([QueuesClient.ackAllQueuesMessages], [QueuesClient.nackAllQueuesMessages],
 * [QueuesClient.reQueueAllMessages]).
 *
 * @see QueuesClient.receiveQueuesMessages
 */
public data class QueueReceiveResponse(
    /** Request identifier for correlation. */
    public val requestId: String,
    /** List of received queue messages. */
    public val messages: List<QueueReceivedMessage>,
    /** `true` if the broker reported an error. */
    public val isError: Boolean,
    /** Error message from the broker; empty if successful. */
    public val error: String,
    /** `true` if the transaction has been completed (all messages acked/rejected). */
    public val isTransactionCompleted: Boolean,
    /** List of active message offsets in this transaction. */
    public val activeOffsets: List<Long>,
    internal val transactionId: String,
    internal val receiverClientId: String,
) {
    internal companion object {
        fun fromProto(
            proto: kubemq.Kubemq.QueuesDownstreamResponse,
            handler: QueueDownstreamHandler?,
            autoAck: Boolean,
            clientId: String = "",
        ): QueueReceiveResponse {
            val messages = proto.messagesList.map { queueMessage ->
                QueueReceivedMessage.fromProto(
                    proto = queueMessage,
                    transactionId = proto.transactionId,
                    receiverClientId = clientId,
                    isAutoAcked = autoAck,
                    handler = handler,
                )
            }
            return QueueReceiveResponse(
                requestId = proto.refRequestId,
                messages = messages,
                isError = proto.isError,
                error = proto.error,
                isTransactionCompleted = proto.transactionComplete,
                activeOffsets = proto.activeOffsetsList.toList(),
                transactionId = proto.transactionId,
                receiverClientId = clientId,
            )
        }

        fun error(requestId: String, message: String): QueueReceiveResponse =
            QueueReceiveResponse(
                requestId = requestId,
                messages = emptyList(),
                isError = true,
                error = message,
                isTransactionCompleted = false,
                activeOffsets = emptyList(),
                transactionId = "",
                receiverClientId = "",
            )
    }
}
