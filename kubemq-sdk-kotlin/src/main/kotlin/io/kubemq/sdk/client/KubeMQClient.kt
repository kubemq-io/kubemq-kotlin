package io.kubemq.sdk.client

import io.kubemq.sdk.common.ChannelDecoder
import io.kubemq.sdk.common.ChannelInfo
import io.kubemq.sdk.common.Defaults
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.exception.toKubeMQException
import io.kubemq.sdk.observability.KubeMQLogger
import io.kubemq.sdk.observability.KubeMQLoggerFactory
import io.kubemq.sdk.transport.GrpcTransport
import io.kubemq.sdk.transport.Transport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstract base class for all KubeMQ client types (PubSub, Queues, CQ).
 *
 * Provides connection lifecycle management, automatic reconnection with
 * configurable exponential backoff, message buffering during disconnections,
 * and channel management operations (create, delete, list).
 *
 * Clients are safe for concurrent use from multiple coroutines. The internal
 * transport is protected by a [Mutex] and all state transitions are atomic.
 *
 * Use the companion factory methods to create typed clients:
 *
 * ```kotlin
 * val pubsub = KubeMQClient.pubSub {
 *     address = "localhost:50000"
 *     clientId = "my-app"
 * }
 *
 * val queues = KubeMQClient.queues {
 *     address = "localhost:50000"
 * }
 *
 * val cq = KubeMQClient.cq {
 *     address = "localhost:50000"
 * }
 * ```
 *
 * @see PubSubClient
 * @see QueuesClient
 * @see CQClient
 * @see ClientConfig
 * @see ConnectionState
 */
public abstract class KubeMQClient internal constructor(
    internal val config: ClientConfig,
) : Closeable {

    public companion object {
        /**
         * Creates a [PubSubClient] for publishing and subscribing to events and events store.
         *
         * @param config Configuration block for [ClientConfig]
         * @return A new [PubSubClient] instance (lazy-connects on first operation)
         */
        public fun pubSub(config: ClientConfig.() -> Unit): io.kubemq.sdk.pubsub.PubSubClient =
            io.kubemq.sdk.pubsub.PubSubClient(ClientConfig().apply(config))

        /**
         * Creates a [QueuesClient] for sending and receiving queue messages.
         *
         * @param config Configuration block for [ClientConfig]
         * @return A new [QueuesClient] instance (lazy-connects on first operation)
         */
        public fun queues(config: ClientConfig.() -> Unit): io.kubemq.sdk.queues.QueuesClient =
            io.kubemq.sdk.queues.QueuesClient(ClientConfig().apply(config))

        /**
         * Creates a [CQClient] for sending commands, queries, and subscribing to requests.
         *
         * @param config Configuration block for [ClientConfig]
         * @return A new [CQClient] instance (lazy-connects on first operation)
         */
        public fun cq(config: ClientConfig.() -> Unit): io.kubemq.sdk.cq.CQClient =
            io.kubemq.sdk.cq.CQClient(ClientConfig().apply(config))

    }

    internal val resolvedAddress: String
    internal val resolvedClientId: String
    internal val logger: KubeMQLogger

    internal val scope: CoroutineScope
    internal val stateMachine = ConnectionStateMachine()
    internal val reconnectionManager: ReconnectionManager
    internal val messageBuffer: MessageBuffer
    internal val subscriptionRegistry = SubscriptionRegistry()

    @Volatile internal var transport: Transport? = null

    private val closed = AtomicBoolean(false)
    private val connectMutex = Mutex()

    private val _reconnectionEvents = MutableSharedFlow<ReconnectionEvent>(
        extraBufferCapacity = 16,
    )
    /** Flow of [ReconnectionEvent] emissions, emitted each time a successful reconnection occurs. */
    public val reconnectionEvents: SharedFlow<ReconnectionEvent> =
        _reconnectionEvents.asSharedFlow()

    internal fun requireTransport(): Transport =
        transport ?: throw KubeMQException.ClientClosed("Client not connected")

    init {
        resolvedAddress = config.address.ifBlank {
            System.getenv("KUBEMQ_ADDRESS") ?: "localhost:50000"
        }
        resolvedClientId = config.clientId.ifBlank {
            System.getenv("KUBEMQ_CLIENT_ID")
                ?: "kubemq-client-${UUID.randomUUID().toString().substring(0, 8)}"
        }
        logger = KubeMQLoggerFactory.create(config.logLevel)
        val exceptionHandler = CoroutineExceptionHandler { _, t ->
            logger.error({ "Unhandled coroutine exception: ${t.message}" }, t)
        }
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO.limitedParallelism(4) + exceptionHandler
        )
        reconnectionManager = ReconnectionManager(config.reconnectionConfig, scope, logger)
        messageBuffer = MessageBuffer(config.bufferConfig)
        config.reconnectionConfig.validate()
    }

    /** Observable [StateFlow] of the current [ConnectionState]. Collect to monitor connection lifecycle changes. */
    public val connectionState: StateFlow<ConnectionState> get() = stateMachine.state

    internal suspend fun ensureConnected() {
        if (closed.get()) throw KubeMQException.ClientClosed()
        if (stateMachine.isReady) return
        connectMutex.withLock {
            if (stateMachine.isReady) return
            val currentState = stateMachine.state.value
            if (currentState is ConnectionState.Idle) {
                connect()
            } else if (currentState is ConnectionState.Reconnecting) {
                // Wait for Ready or Closed with timeout
                withTimeout(config.ensureConnectedTimeoutMs) {
                    stateMachine.state.first {
                        it is ConnectionState.Ready || it is ConnectionState.Closed
                    }
                }
                if (stateMachine.isClosed) throw KubeMQException.ClientClosed()
                if (!stateMachine.isReady) throw KubeMQException.Connection(
                    "Connection not ready after waiting",
                    operation = "ensureConnected",
                )
            }
        }
    }

    private suspend fun connect() {
        stateMachine.transitionTo(ConnectionState.Connecting)
        try {
            val t = createTransport()
            t.connect()
            t.ping()
            transport = t
            stateMachine.transitionTo(ConnectionState.Ready)
            logger.info { "Connected to $resolvedAddress" }
        } catch (e: Exception) {
            logger.error({ "Connection failed: ${e.message}" }, e)
            startReconnection()
        }
    }

    internal fun startReconnection() {
        stateMachine.transitionTo(ConnectionState.Reconnecting(1))
        reconnectionManager.startReconnection(
            reconnectAction = {
                transport?.close()
                val t = createTransport()
                t.connect()
                transport = t
            },
            pingCheck = { requireTransport().ping() },
            onSuccess = {
                val reconnectAttempt = (stateMachine.state.value as? ConnectionState.Reconnecting)?.attempt ?: 0
                stateMachine.transitionTo(ConnectionState.Ready)
                // Replay buffered messages in FIFO order
                val buffered = messageBuffer.drain()
                if (buffered.isNotEmpty()) {
                    logger.info { "Replaying ${buffered.size} buffered messages" }
                    for (msg in buffered) {
                        try {
                            msg.replayAction(this@KubeMQClient)
                        } catch (e: Exception) {
                            logger.warn { "Failed to replay buffered message: ${e.message}" }
                        }
                    }
                }
                // Re-subscribe all active subscriptions
                subscriptionRegistry.resubscribeAll(this@KubeMQClient, scope)
                // Emit reconnection event
                _reconnectionEvents.tryEmit(
                    ReconnectionEvent(
                        attempt = reconnectAttempt,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            },
            onFailed = {
                stateMachine.transitionTo(ConnectionState.Closed)
            },
        )
    }

    private fun createTransport(): GrpcTransport {
        val resolvedToken = config.authToken.ifBlank {
            System.getenv("KUBEMQ_AUTH_TOKEN") ?: ""
        }
        return GrpcTransport(
            address = resolvedAddress,
            tlsConfig = config.tls,
            maxReceiveSize = config.maxReceiveSize,
            keepAlive = config.keepAlive,
            pingIntervalSeconds = config.pingIntervalSeconds,
            pingTimeoutSeconds = config.pingTimeoutSeconds,
            tokenSupplier = { resolvedToken.ifBlank { null } },
            unaryTimeoutMs = config.unaryTimeoutMs,
        )
    }

    /**
     * Pings the KubeMQ broker and returns server information.
     *
     * Establishes a connection if not already connected (lazy connect).
     *
     * @return [ServerInfo] containing host, version, and uptime details
     * @throws KubeMQException.Connection if the broker is unreachable
     * @throws KubeMQException.ClientClosed if the client has been closed
     */
    public suspend fun ping(): ServerInfo {
        ensureConnected()
        return requireTransport().ping()
    }

    /**
     * Closes the client, releasing all resources.
     *
     * Cancels any active reconnection, discards buffered messages, shuts down
     * the gRPC transport, and cancels the internal [CoroutineScope]. This method
     * is idempotent -- calling it multiple times has no additional effect.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        logger.info { "Shutting down client" }

        val currentState = stateMachine.state.value
        if (currentState is ConnectionState.Reconnecting) {
            reconnectionManager.cancel()
            kotlinx.coroutines.runBlocking(Dispatchers.IO) { messageBuffer.discardAll() }
        }

        transport?.close()
        reconnectionManager.cancel()
        scope.cancel()
        stateMachine.transitionTo(ConnectionState.Closed)
    }

    private fun buildChannelMgmtRequest(
        metadata: String,
        channelType: String,
        channelName: String? = null,
        search: String? = null,
    ): kubemq.Kubemq.Request {
        val builder = kubemq.Kubemq.Request.newBuilder()
            .setRequestID(UUID.randomUUID().toString())
            .setRequestTypeData(kubemq.Kubemq.Request.RequestType.Query)
            .setMetadata(metadata)
            .setChannel(Defaults.REQUESTS_CHANNEL)
            .setClientID(resolvedClientId)
            .putTags("channel_type", channelType)
            .putTags("client_id", resolvedClientId)
            .setTimeout(Defaults.CHANNEL_MGMT_TIMEOUT_MS)
        if (channelName != null) {
            builder.putTags("channel", channelName)
        }
        if (!search.isNullOrBlank()) {
            builder.putTags("channel_search", search)
        }
        return builder.build()
    }

    private suspend fun executeChannelMgmt(
        operation: String,
        channelType: String,
        channelName: String?,
    ): kubemq.Kubemq.Response {
        try {
            val response = requireTransport().getUnaryStub().sendRequest(
                buildChannelMgmtRequest(operation, channelType, channelName),
            )
            if (!response.executed) {
                throw KubeMQException.Server(
                    response.error, operation = operation, channel = channelName,
                )
            }
            return response
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException(operation, channelName)
        }
    }

    internal suspend fun createChannel(channelName: String, channelType: String) {
        ensureConnected()
        Validation.validateChannel(channelName, "createChannel")
        executeChannelMgmt("create-channel", channelType, channelName)
    }

    internal suspend fun deleteChannel(channelName: String, channelType: String) {
        ensureConnected()
        Validation.validateChannel(channelName, "deleteChannel")
        executeChannelMgmt("delete-channel", channelType, channelName)
    }

    internal suspend fun listChannels(channelType: String, search: String): List<ChannelInfo> {
        ensureConnected()
        try {
            val request = buildChannelMgmtRequest("list-channels", channelType, search = search)
            val response = requireTransport().getUnaryStub().sendRequest(request)
            if (!response.executed) {
                throw KubeMQException.Server(response.error, operation = "listChannels")
            }
            return ChannelDecoder.decodeChannelList(response.body.toByteArray())
        } catch (e: io.grpc.StatusRuntimeException) {
            throw e.toKubeMQException("listChannels")
        }
    }
}
