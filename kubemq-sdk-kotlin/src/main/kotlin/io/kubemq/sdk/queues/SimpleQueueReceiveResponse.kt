package io.kubemq.sdk.queues

/**
 * Response from receiving queue messages via the simple (unary) API.
 *
 * All messages in this response are auto-acknowledged.
 *
 * @see QueuesClient.receiveQueueMessages
 * @see QueuesClient.peekQueueMessages
 */
public data class SimpleQueueReceiveResponse(
    /** Request identifier for correlation. */
    public val requestId: String,
    /** List of received queue messages. */
    public val messages: List<QueueReceivedMessage>,
    /** `true` if the broker reported an error. */
    public val isError: Boolean,
    /** Error message from the broker; empty if successful. */
    public val error: String,
    /** Number of messages received. */
    public val messagesReceived: Int,
    /** Number of messages that expired before delivery. */
    public val messagesExpired: Int,
    /** `true` if this was a peek operation (messages not removed from queue). */
    public val isPeek: Boolean,
) {
    internal companion object {
        fun fromProto(
            proto: kubemq.Kubemq.ReceiveQueueMessagesResponse,
            clientId: String,
        ): SimpleQueueReceiveResponse {
            val messages = proto.messagesList.map { queueMessage ->
                QueueReceivedMessage.fromSimpleProto(queueMessage, clientId)
            }
            return SimpleQueueReceiveResponse(
                requestId = proto.requestID,
                messages = messages,
                isError = proto.isError,
                error = proto.error,
                messagesReceived = proto.messagesReceived,
                messagesExpired = proto.messagesExpired,
                isPeek = proto.isPeak,
            )
        }

        fun error(requestId: String, message: String): SimpleQueueReceiveResponse =
            SimpleQueueReceiveResponse(
                requestId = requestId,
                messages = emptyList(),
                isError = true,
                error = message,
                messagesReceived = 0,
                messagesExpired = 0,
                isPeek = false,
            )
    }
}
