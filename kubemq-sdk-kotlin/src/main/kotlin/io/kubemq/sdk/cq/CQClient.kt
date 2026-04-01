package io.kubemq.sdk.cq

import io.kubemq.sdk.client.ClientConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ChannelInfo
import io.kubemq.sdk.common.ChannelType
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.exception.toKubeMQException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Client for sending commands and queries, and subscribing to incoming requests.
 *
 * Commands are fire-and-execute with a boolean response. Queries are request-response
 * with a data payload. Both support configurable timeouts.
 *
 * This client is safe for concurrent use from multiple coroutines. Subscription
 * [Flow]s support structured concurrency -- cancelling the collecting coroutine
 * automatically cleans up the underlying gRPC stream.
 *
 * Create via the factory method:
 * ```kotlin
 * val cq = KubeMQClient.cq {
 *     address = "localhost:50000"
 *     clientId = "my-service"
 * }
 * ```
 *
 * @see KubeMQClient.cq
 * @see CommandMessage
 * @see QueryMessage
 */
public class CQClient internal constructor(config: ClientConfig) : KubeMQClient(config) {

    // -- Commands: send --

    /**
     * Sends a command to the specified channel and waits for a response.
     *
     * The command is delivered to exactly one subscriber (or a consumer group member).
     * The call blocks until the responder replies or the timeout expires.
     *
     * @param message The command message to send
     * @return [CommandResponse] with the execution result
     * @throws KubeMQException.Validation if channel is blank or contains wildcards, or timeoutMs is <= 0
     * @throws KubeMQException.Timeout if no response is received within [CommandMessage.timeoutMs]
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun sendCommand(message: CommandMessage): CommandResponse {
        ensureConnected()
        message.validate()
        val request = message.toProto(resolvedClientId)
        try {
            val response = requireTransport().getUnaryStub().sendRequest(request)   // M-11
            logger.debug { "sendCommand response received for ${request.requestID}" }
            return CommandResponse.decode(response)
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("sendCommand", message.channel, request.requestID)
        }
    }

    // -- Commands: subscribe --

    /**
     * Subscribes to incoming commands on the specified channel and returns a [Flow].
     *
     * Each [CommandReceived] must be responded to via [sendCommandResponse] within the
     * sender's timeout window. The returned [Flow] supports structured concurrency:
     * cancelling the collecting coroutine cancels the underlying gRPC stream.
     *
     * @param config Configuration block for [CommandsSubscriptionConfig] (channel, group)
     * @return Cold [Flow] of [CommandReceived] -- collection starts the subscription
     * @throws KubeMQException.Validation if channel is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public fun subscribeToCommands(                                                // H-2
        config: CommandsSubscriptionConfig.() -> Unit,
    ): Flow<CommandReceived> {
        val cfg = CommandsSubscriptionConfig().apply(config)
        Validation.validateChannel(cfg.channel, "subscribeToCommands")             // FIX-4: eager validation
        return callbackFlow {
            ensureConnected()                                                      // H-2
            val subscribe = kubemq.Kubemq.Subscribe.newBuilder()
                .setChannel(cfg.channel)
                .setGroup(cfg.group)
                .setClientID(resolvedClientId)
                .setSubscribeTypeData(kubemq.Kubemq.Subscribe.SubscribeType.Commands)
                .setSubscribeTypeDataValue(kubemq.Kubemq.Subscribe.SubscribeType.Commands_VALUE)
                .build()
            logger.info { "Subscribing to commands on channel=${cfg.channel}, group=${cfg.group}" }
            val responseFlow = requireTransport().getStreamingStub()               // M-11
                .subscribeToRequests(subscribe)
            val collectJob = launch {
                try {
                    responseFlow.collect { request -> send(CommandReceived.decode(request)) }
                    channel.close()                                                // stream completed normally
                } catch (e: io.grpc.StatusRuntimeException) {                      // M-9
                    close(e.toKubeMQException("subscribeToCommands", cfg.channel))
                }
            }
            awaitClose { collectJob.cancel() }
        }
    }

    // -- Commands: send response --

    /**
     * Sends a response to a received command.
     *
     * Call this with a [CommandResponseMessage] built from [CommandReceived.respond].
     *
     * @param message The command response message to send
     * @throws KubeMQException.Validation if the reply channel is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun sendCommandResponse(message: CommandResponseMessage) {
        ensureConnected()
        message.validate()
        val proto = message.toProto(resolvedClientId)
        try {
            requireTransport().getUnaryStub().sendResponse(proto)                  // M-11
            logger.debug { "sendCommandResponse sent for requestId=${message.requestId}" }
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("sendCommandResponse")
        }
    }

    // -- Queries: send --

    /**
     * Sends a query to the specified channel and waits for a response with data.
     *
     * The query is delivered to exactly one subscriber (or a consumer group member).
     * Responses can optionally be cached using [QueryMessage.cacheKey] and
     * [QueryMessage.cacheTtlSeconds].
     *
     * @param message The query message to send
     * @return [QueryResponse] with the response data
     * @throws KubeMQException.Validation if channel is blank or contains wildcards, or timeoutMs is <= 0
     * @throws KubeMQException.Timeout if no response is received within [QueryMessage.timeoutMs]
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun sendQuery(message: QueryMessage): QueryResponse {
        ensureConnected()
        message.validate()
        val request = message.toProto(resolvedClientId)
        try {
            val response = requireTransport().getUnaryStub().sendRequest(request)   // M-11
            logger.debug { "sendQuery response received for ${request.requestID}" }
            return QueryResponse.decode(response)
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("sendQuery", message.channel, request.requestID)
        }
    }

    // -- Queries: subscribe --

    /**
     * Subscribes to incoming queries on the specified channel and returns a [Flow].
     *
     * Each [QueryReceived] must be responded to via [sendQueryResponse] within the
     * sender's timeout window. The returned [Flow] supports structured concurrency:
     * cancelling the collecting coroutine cancels the underlying gRPC stream.
     *
     * @param config Configuration block for [QueriesSubscriptionConfig] (channel, group)
     * @return Cold [Flow] of [QueryReceived] -- collection starts the subscription
     * @throws KubeMQException.Validation if channel is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public fun subscribeToQueries(                                                 // H-2
        config: QueriesSubscriptionConfig.() -> Unit,
    ): Flow<QueryReceived> {
        val cfg = QueriesSubscriptionConfig().apply(config)
        Validation.validateChannel(cfg.channel, "subscribeToQueries")              // FIX-4: eager validation
        return callbackFlow {
            ensureConnected()                                                      // H-2
            val subscribe = kubemq.Kubemq.Subscribe.newBuilder()
                .setChannel(cfg.channel)
                .setGroup(cfg.group)
                .setClientID(resolvedClientId)
                .setSubscribeTypeData(kubemq.Kubemq.Subscribe.SubscribeType.Queries)
                .setSubscribeTypeDataValue(kubemq.Kubemq.Subscribe.SubscribeType.Queries_VALUE)
                .build()
            logger.info { "Subscribing to queries on channel=${cfg.channel}, group=${cfg.group}" }
            val responseFlow = requireTransport().getStreamingStub()               // M-11
                .subscribeToRequests(subscribe)
            val collectJob = launch {
                try {
                    responseFlow.collect { request -> send(QueryReceived.decode(request)) }
                    channel.close()                                                // stream completed normally
                } catch (e: io.grpc.StatusRuntimeException) {                      // M-9
                    close(e.toKubeMQException("subscribeToQueries", cfg.channel))
                }
            }
            awaitClose { collectJob.cancel() }
        }
    }

    // -- Queries: send response --

    /**
     * Sends a response to a received query.
     *
     * Call this with a [QueryResponseMessage] built from [QueryReceived.respond].
     *
     * @param message The query response message to send
     * @throws KubeMQException.Validation if the reply channel is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun sendQueryResponse(message: QueryResponseMessage) {
        ensureConnected()
        message.validate()
        val proto = message.toProto(resolvedClientId)
        try {
            requireTransport().getUnaryStub().sendResponse(proto)                  // M-11
            logger.debug { "sendQueryResponse sent for requestId=${message.requestId}" }
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("sendQueryResponse")
        }
    }

    // -- Channel management --

    /**
     * Creates a commands channel on the broker.
     *
     * @param channelName The channel name to create
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun createCommandsChannel(channelName: String) {
        createChannel(channelName, ChannelType.COMMANDS.value)
    }

    /**
     * Creates a queries channel on the broker.
     *
     * @param channelName The channel name to create
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun createQueriesChannel(channelName: String) {
        createChannel(channelName, ChannelType.QUERIES.value)
    }

    /**
     * Deletes a commands channel from the broker.
     *
     * @param channelName The channel name to delete
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun deleteCommandsChannel(channelName: String) {
        deleteChannel(channelName, ChannelType.COMMANDS.value)
    }

    /**
     * Deletes a queries channel from the broker.
     *
     * @param channelName The channel name to delete
     * @throws KubeMQException.Validation if channel name is blank
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun deleteQueriesChannel(channelName: String) {
        deleteChannel(channelName, ChannelType.QUERIES.value)
    }

    /**
     * Lists commands channels, optionally filtered by a search string.
     *
     * @param search Optional filter string for channel names. Default: `""` (all channels).
     * @return List of [ChannelInfo] matching the search criteria
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun listCommandsChannels(search: String = ""): List<ChannelInfo> =
        listChannels(ChannelType.COMMANDS.value, search)

    /**
     * Lists queries channels, optionally filtered by a search string.
     *
     * @param search Optional filter string for channel names. Default: `""` (all channels).
     * @return List of [ChannelInfo] matching the search criteria
     * @throws KubeMQException.Connection if the broker is unreachable
     */
    public suspend fun listQueriesChannels(search: String = ""): List<ChannelInfo> =
        listChannels(ChannelType.QUERIES.value, search)
}
