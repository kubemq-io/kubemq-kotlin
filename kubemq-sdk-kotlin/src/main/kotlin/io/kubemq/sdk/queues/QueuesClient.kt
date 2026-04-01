package io.kubemq.sdk.queues

import io.kubemq.sdk.client.ClientConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ChannelInfo
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.exception.toKubeMQException
import java.util.UUID

/**
 * Client for sending and receiving messages on KubeMQ queue channels.
 *
 * Provides two APIs:
 * - **Stream API** -- High-performance bidirectional streaming with manual ack/reject
 *   ([sendQueuesMessage], [receiveQueuesMessages], [ackAllQueuesMessages])
 * - **Simple API** -- Unary gRPC calls for simpler use cases
 *   ([sendQueueMessage], [receiveQueueMessages], [peekQueueMessages])
 *
 * This client is safe for concurrent use from multiple coroutines.
 * [QueueReceivedMessage.ack], [reject][QueueReceivedMessage.reject], and
 * [reQueue][QueueReceivedMessage.reQueue] are thread-safe (protected by an
 * [AtomicBoolean][java.util.concurrent.atomic.AtomicBoolean] guard).
 *
 * Create via the factory method:
 * ```kotlin
 * val queues = KubeMQClient.queues {
 *     address = "localhost:50000"
 *     clientId = "queue-worker"
 * }
 * ```
 *
 * @see KubeMQClient.queues
 * @see QueueMessage
 * @see QueueReceivedMessage
 */
public class QueuesClient internal constructor(config: ClientConfig) : KubeMQClient(config) {

    private val upstreamHandler: QueueUpstreamHandler by lazy {
        QueueUpstreamHandler(this, logger)
    }
    private val downstreamHandler: QueueDownstreamHandler by lazy {
        QueueDownstreamHandler(this, logger)
    }

    // ---- Stream API: Send ----

    /**
     * Sends a single message to a queue channel using the stream API.
     *
     * @param message The queue message to send
     * @return [QueueSendResult] with send confirmation
     * @throws KubeMQException.Validation if channel is blank, contains wildcards, or message has no content
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun sendQueuesMessage(message: QueueMessage): QueueSendResult {
        ensureConnected()
        message.validate()
        logger.debug { "Sending queue message to channel: ${message.channel}" }
        return upstreamHandler.sendSingle(message)
    }

    /**
     * Sends a batch of messages to queue channels using the stream API.
     *
     * @param messages List of queue messages to send (must not be empty)
     * @return List of [QueueSendResult] in the same order as input messages
     * @throws KubeMQException.Validation if the list is empty or any message is invalid
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun sendQueuesMessages(messages: List<QueueMessage>): List<QueueSendResult> {
        ensureConnected()
        if (messages.isEmpty()) {
            throw KubeMQException.Validation(
                "Queue messages list cannot be empty",
                operation = "sendQueuesMessages",
            )
        }
        messages.forEach { it.validate() }
        logger.debug { "Sending ${messages.size} queue messages in batch" }
        return upstreamHandler.sendBatch(messages)
    }

    // ---- Stream API: Receive ----

    /**
     * Receives messages from a queue channel using the stream API.
     *
     * When [QueueReceiveConfig.autoAck] is `false`, each returned [QueueReceivedMessage]
     * must be explicitly acknowledged, rejected, or re-queued.
     *
     * @param config The receive configuration
     * @return [QueueReceiveResponse] containing the received messages
     * @throws KubeMQException.Validation if channel is blank or parameters are invalid
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun receiveQueuesMessages(config: QueueReceiveConfig): QueueReceiveResponse {
        ensureConnected()
        config.validate()
        logger.debug { "Receiving queue messages from channel: ${config.channel}" }
        return downstreamHandler.poll(config)
    }

    /**
     * Receives messages from a queue channel using the stream API with a DSL builder.
     *
     * @param init Configuration block for [QueueReceiveConfig]
     * @return [QueueReceiveResponse] containing the received messages
     * @throws KubeMQException.Validation if channel is blank or parameters are invalid
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun receiveQueuesMessages(
        init: QueueReceiveConfig.() -> Unit,
    ): QueueReceiveResponse =
        receiveQueuesMessages(QueueReceiveConfig().apply(init))

    // ---- Stream API: Bulk Operations ----

    /**
     * Acknowledges all messages in a [QueueReceiveResponse] (stream API).
     *
     * @param response The receive response whose messages should be acknowledged
     * @throws IllegalStateException if the transaction is already completed or has no transaction ID
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun ackAllQueuesMessages(response: QueueReceiveResponse) {
        ensureConnected()
        check(!response.isTransactionCompleted) { "Transaction already completed" }
        check(response.transactionId.isNotBlank()) { "No transaction ID" }
        logger.debug { "Ack all messages for transaction: ${response.transactionId}" }
        downstreamHandler.sendAckAll(
            response.transactionId,
            response.receiverClientId,
            response.activeOffsets,
        )
    }

    /**
     * Rejects (nacks) all messages in a [QueueReceiveResponse], returning them to the queue (stream API).
     *
     * @param response The receive response whose messages should be rejected
     * @throws IllegalStateException if the transaction is already completed or has no transaction ID
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun nackAllQueuesMessages(response: QueueReceiveResponse) {
        ensureConnected()
        check(!response.isTransactionCompleted) { "Transaction already completed" }
        check(response.transactionId.isNotBlank()) { "No transaction ID" }
        logger.debug { "Nack all messages for transaction: ${response.transactionId}" }
        downstreamHandler.sendNackAll(
            response.transactionId,
            response.receiverClientId,
            response.activeOffsets,
        )
    }

    /**
     * Re-queues all messages in a [QueueReceiveResponse] to a different channel (stream API).
     *
     * @param response The receive response whose messages should be re-queued
     * @param channel The target queue channel for re-queuing (must not be blank)
     * @throws IllegalArgumentException if [channel] is blank
     * @throws IllegalStateException if the transaction is already completed
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun reQueueAllMessages(response: QueueReceiveResponse, channel: String) {
        ensureConnected()
        require(channel.isNotBlank()) { "Re-queue channel cannot be empty" }
        check(!response.isTransactionCompleted) { "Transaction already completed" }
        check(response.transactionId.isNotBlank()) { "No transaction ID" }
        logger.debug { "Re-queue all messages for transaction: ${response.transactionId} to $channel" }
        downstreamHandler.sendReQueueAll(
            response.transactionId,
            response.receiverClientId,
            response.activeOffsets,
            channel,
        )
    }

    // ---- Simple API: Send ----

    /**
     * Sends a single message to a queue channel using the simple (unary) API.
     *
     * @param message The queue message to send
     * @return [QueueSendResult] with send confirmation
     * @throws KubeMQException.Validation if the message is invalid
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun sendQueueMessage(message: QueueMessage): QueueSendResult {
        ensureConnected()
        message.validate()
        logger.debug { "Simple send queue message to channel: ${message.channel}" }
        try {
            val result = requireTransport().getUnaryStub()
                .sendQueueMessage(message.toProto(resolvedClientId))
            return QueueSendResult.fromProto(result)
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("sendQueueMessage", message.channel)
        }
    }

    /**
     * Sends a batch of messages to queue channels using the simple (unary) API.
     *
     * @param messages List of queue messages to send (must not be empty)
     * @return List of [QueueSendResult] in the same order as input messages
     * @throws KubeMQException.Validation if the list is empty or any message is invalid
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun sendQueueMessagesBatch(messages: List<QueueMessage>): List<QueueSendResult> {
        ensureConnected()
        if (messages.isEmpty()) {
            throw KubeMQException.Validation(
                "Queue messages list cannot be empty",
                operation = "sendQueueMessagesBatch",
            )
        }
        messages.forEach { it.validate() }
        logger.debug { "Simple batch send ${messages.size} queue messages" }
        try {
            val batchRequest = kubemq.Kubemq.QueueMessagesBatchRequest.newBuilder()
                .setBatchID(UUID.randomUUID().toString())
                .addAllMessages(messages.map { it.toProto(resolvedClientId) })
                .build()
            val response = requireTransport().getUnaryStub()
                .sendQueueMessagesBatch(batchRequest)
            return response.resultsList.map { QueueSendResult.fromProto(it) }
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("sendQueueMessagesBatch")
        }
    }

    // ---- Simple API: Receive ----

    /**
     * Receives messages from a queue using the simple (unary) API.
     *
     * Messages received via the simple API are auto-acknowledged.
     *
     * @param config The receive configuration
     * @return [SimpleQueueReceiveResponse] containing the received messages
     * @throws KubeMQException.Validation if channel is blank or parameters are invalid
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun receiveQueueMessages(config: SimpleQueueReceiveConfig): SimpleQueueReceiveResponse {
        ensureConnected()
        config.validate()
        logger.debug { "Simple receive queue messages from channel: ${config.channel}" }
        try {
            val response = requireTransport().getUnaryStub()
                .receiveQueueMessages(config.toProto(resolvedClientId, isPeek = false))
            return SimpleQueueReceiveResponse.fromProto(response, resolvedClientId)
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("receiveQueueMessages", config.channel)
        }
    }

    /**
     * Receives messages from a queue using the simple (unary) API with a DSL builder.
     *
     * @param init Configuration block for [SimpleQueueReceiveConfig]
     * @return [SimpleQueueReceiveResponse] containing the received messages
     */
    public suspend fun receiveQueueMessages(
        init: SimpleQueueReceiveConfig.() -> Unit,
    ): SimpleQueueReceiveResponse =
        receiveQueueMessages(SimpleQueueReceiveConfig().apply(init))

    /**
     * Peeks at messages in a queue without removing them (simple API).
     *
     * @param config The receive configuration
     * @return [SimpleQueueReceiveResponse] containing the peeked messages
     * @throws KubeMQException.Validation if channel is blank or parameters are invalid
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun peekQueueMessages(config: SimpleQueueReceiveConfig): SimpleQueueReceiveResponse {
        ensureConnected()
        config.validate()
        logger.debug { "Peek queue messages from channel: ${config.channel}" }
        try {
            val response = requireTransport().getUnaryStub()
                .receiveQueueMessages(config.toProto(resolvedClientId, isPeek = true))
            return SimpleQueueReceiveResponse.fromProto(response, resolvedClientId)
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("peekQueueMessages", config.channel)
        }
    }

    /**
     * Peeks at messages in a queue without removing them with a DSL builder (simple API).
     *
     * @param init Configuration block for [SimpleQueueReceiveConfig]
     * @return [SimpleQueueReceiveResponse] containing the peeked messages
     */
    public suspend fun peekQueueMessages(
        init: SimpleQueueReceiveConfig.() -> Unit,
    ): SimpleQueueReceiveResponse =
        peekQueueMessages(SimpleQueueReceiveConfig().apply(init))

    // ---- Simple API: Ack All ----

    /**
     * Acknowledges all pending messages on a queue channel (simple API).
     *
     * @param channel The queue channel name
     * @return Number of messages that were acknowledged
     * @throws KubeMQException.Validation if channel is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.Server if the broker reports an error
     */
    public suspend fun ackAllQueueMessages(channel: String): Long {
        ensureConnected()
        Validation.validateChannel(channel, "ackAllQueueMessages")
        logger.debug { "Ack all queue messages on channel: $channel" }
        try {
            val request = kubemq.Kubemq.AckAllQueueMessagesRequest.newBuilder()
                .setRequestID(UUID.randomUUID().toString())
                .setClientID(resolvedClientId)
                .setChannel(channel)
                .setWaitTimeSeconds(5)
                .build()
            val response = requireTransport().getUnaryStub()
                .ackAllQueueMessages(request)
            if (response.isError) {
                throw KubeMQException.Server(
                    response.error,
                    operation = "ackAllQueueMessages",
                    channel = channel,
                )
            }
            return response.affectedMessages
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("ackAllQueueMessages", channel)
        }
    }

    // ---- Channel Management ----

    /**
     * Creates a queues channel on the broker.
     *
     * @param channelName The channel name to create
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun createQueuesChannel(channelName: String) {
        createChannel(channelName, "queues")
    }

    /**
     * Deletes a queues channel from the broker.
     *
     * @param channelName The channel name to delete
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun deleteQueuesChannel(channelName: String) {
        deleteChannel(channelName, "queues")
    }

    /**
     * Lists queues channels, optionally filtered by a search string.
     *
     * @param search Optional filter string for channel names. Default: `""` (all channels).
     * @return List of [ChannelInfo] matching the search criteria
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun listQueuesChannels(search: String = ""): List<ChannelInfo> =
        listChannels("queues", search)

    /**
     * Purges all messages from a queue channel by acknowledging them.
     *
     * @param channelName The queue channel to purge
     * @return Number of messages purged
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun purgeQueuesChannel(channelName: String): Long =
        ackAllQueueMessages(channelName)

    /**
     * Closes this client, releasing all resources.
     *
     * Shuts down the upstream and downstream stream handlers, then closes
     * the underlying gRPC transport. After calling this method, any further
     * operations on this client will throw [KubeMQException.ClientClosed].
     */
    override fun close() {
        try {
            upstreamHandler.close()
        } catch (_: Exception) {
            // Ignore
        }
        try {
            downstreamHandler.close()
        } catch (_: Exception) {
            // Ignore
        }
        super.close()
    }
}
