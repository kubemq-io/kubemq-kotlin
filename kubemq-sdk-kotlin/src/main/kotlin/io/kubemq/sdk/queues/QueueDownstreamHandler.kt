package io.kubemq.sdk.queues

import io.grpc.stub.StreamObserver
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.observability.KubeMQLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class QueueDownstreamHandler(
    private val client: KubeMQClient,
    private val logger: KubeMQLogger,
) {
    private val isConnected = AtomicBoolean(false)
    private val sendLock = Any()

    @Volatile
    private var requestsObserver: StreamObserver<kubemq.Kubemq.QueuesDownstreamRequest>? = null

    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<QueueReceiveResponse>>()
    private val pendingConfigs = ConcurrentHashMap<String, QueueReceiveConfig>()
    private val requestTimestamps = ConcurrentHashMap<String, Long>()

    private val cleaner = StaleRequestCleaner("downstream", logger)

    companion object {
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }

    private fun connect() {
        if (isConnected.get()) return
        synchronized(this) {
            if (isConnected.get()) return
            try {
                cleaner.start(Runnable { cleanupStaleRequests() })

                val responsesObserver = object : StreamObserver<kubemq.Kubemq.QueuesDownstreamResponse> {
                    override fun onNext(response: kubemq.Kubemq.QueuesDownstreamResponse) {
                        val refId = response.refRequestId

                        val future = pendingResponses.remove(refId)
                        val config = pendingConfigs.remove(refId)
                        requestTimestamps.remove(refId)

                        if (future != null) {
                            val autoAck = config?.autoAck ?: false
                            val result = QueueReceiveResponse.fromProto(response, this@QueueDownstreamHandler, autoAck, client.resolvedClientId)
                            future.complete(result)
                        }
                    }

                    override fun onError(t: Throwable) {
                        logger.error({ "Error in downstream response observer: ${t.message}" }, t)
                        closeStreamWithError(t.message ?: "Stream error")
                    }

                    override fun onCompleted() {
                        logger.info { "Downstream response stream completed" }
                        closeStreamWithError("Stream completed")
                    }
                }

                requestsObserver = client.requireTransport().getAsyncStub().queuesDownstream(responsesObserver)
                isConnected.set(true)
            } catch (e: Exception) {
                logger.error({ "Error initializing downstream stream: ${e.message}" }, e)
                isConnected.set(false)
                throw e
            }
        }
    }

    private fun closeStreamWithError(message: String) {
        isConnected.set(false)
        pendingResponses.forEach { (_, future) ->
            future.complete(QueueReceiveResponse.error("", message))
        }
        pendingResponses.clear()
        pendingConfigs.clear()
        requestTimestamps.clear()
    }

    private fun cleanupStaleRequests() {
        cleaner.cleanupStaleEntries(
            pendingResponses,
            requestTimestamps,
            QueueReceiveResponse::error,
            extraCleanup = { key -> pendingConfigs.remove(key) },
        )
    }

    suspend fun poll(config: QueueReceiveConfig): QueueReceiveResponse {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<QueueReceiveResponse>()

        pendingResponses[requestId] = deferred
        pendingConfigs[requestId] = config
        requestTimestamps[requestId] = System.currentTimeMillis()

        try {
            val request = config.toProto(client.resolvedClientId).toBuilder()
                .setRequestID(requestId)
                .build()
            sendRequest(request)
        } catch (e: Exception) {
            pendingResponses.remove(requestId)
            pendingConfigs.remove(requestId)
            requestTimestamps.remove(requestId)
            logger.error({ "Error polling queue messages: ${e.message}" }, e)
            return QueueReceiveResponse.error(requestId, "Failed to poll: ${e.message}")
        }

        val timeoutMs = config.waitTimeoutMs.toLong() + 10_000L
        return withTimeout(timeoutMs) { deferred.await() }
    }

    private fun buildTransactionRequest(
        type: kubemq.Kubemq.QueuesDownstreamRequestType,
        transactionId: String,
        receiverClientId: String,
        sequences: List<Long>,
        reQueueChannel: String? = null,
    ): kubemq.Kubemq.QueuesDownstreamRequest {
        val builder = kubemq.Kubemq.QueuesDownstreamRequest.newBuilder()
            .setRequestID(UUID.randomUUID().toString())
            .setClientID(receiverClientId)
            .setRequestTypeData(type)
            .setRefTransactionId(transactionId)
            .addAllSequenceRange(sequences)
        if (reQueueChannel != null) {
            builder.setReQueueChannel(reQueueChannel)
        }
        return builder.build()
    }

    fun sendAck(transactionId: String, receiverClientId: String, sequence: Long) {
        sendRequest(buildTransactionRequest(
            kubemq.Kubemq.QueuesDownstreamRequestType.AckRange,
            transactionId, receiverClientId, listOf(sequence),
        ))
    }

    fun sendReject(transactionId: String, receiverClientId: String, sequence: Long) {
        sendRequest(buildTransactionRequest(
            kubemq.Kubemq.QueuesDownstreamRequestType.NAckRange,
            transactionId, receiverClientId, listOf(sequence),
        ))
    }

    fun sendReQueue(transactionId: String, receiverClientId: String, sequence: Long, channel: String) {
        sendRequest(buildTransactionRequest(
            kubemq.Kubemq.QueuesDownstreamRequestType.ReQueueRange,
            transactionId, receiverClientId, listOf(sequence), channel,
        ))
    }

    fun sendAckAll(transactionId: String, receiverClientId: String, activeOffsets: List<Long>) {
        sendRequest(buildTransactionRequest(
            kubemq.Kubemq.QueuesDownstreamRequestType.AckAll,
            transactionId, receiverClientId, activeOffsets,
        ))
    }

    fun sendNackAll(transactionId: String, receiverClientId: String, activeOffsets: List<Long>) {
        sendRequest(buildTransactionRequest(
            kubemq.Kubemq.QueuesDownstreamRequestType.NAckAll,
            transactionId, receiverClientId, activeOffsets,
        ))
    }

    fun sendReQueueAll(transactionId: String, receiverClientId: String, activeOffsets: List<Long>, channel: String) {
        sendRequest(buildTransactionRequest(
            kubemq.Kubemq.QueuesDownstreamRequestType.ReQueueAll,
            transactionId, receiverClientId, activeOffsets, channel,
        ))
    }

    private fun sendRequest(request: kubemq.Kubemq.QueuesDownstreamRequest) {
        if (!isConnected.get()) {
            connect()
        }
        val updatedRequest = request.toBuilder()
            .setClientID(client.resolvedClientId)
            .build()
        synchronized(sendLock) {
            val observer = requestsObserver
            if (observer != null) {
                try {
                    observer.onNext(updatedRequest)
                } catch (e: Exception) {
                    isConnected.set(false)
                    throw e
                }
            } else {
                throw KubeMQException.StreamBroken("Stream not connected", operation = "sendRequest")
            }
        }
    }

    fun close() {
        isConnected.set(false)
        try {
            synchronized(sendLock) {
                requestsObserver?.onCompleted()
                requestsObserver = null                                                // M-1
            }
        } catch (_: Exception) {
            synchronized(sendLock) { requestsObserver = null }                         // M-1
        }
        cleaner.stop()                                                                 // M-3
        closeStreamWithError("Handler closed")
    }
}
