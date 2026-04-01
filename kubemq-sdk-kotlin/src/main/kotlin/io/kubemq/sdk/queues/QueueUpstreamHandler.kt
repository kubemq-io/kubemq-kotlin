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

internal class QueueUpstreamHandler(
    private val client: KubeMQClient,
    private val logger: KubeMQLogger,
) {
    private val isConnected = AtomicBoolean(false)
    private val sendLock = Any()

    @Volatile
    private var requestsObserver: StreamObserver<kubemq.Kubemq.QueuesUpstreamRequest>? = null

    private val pendingSingle = ConcurrentHashMap<String, CompletableDeferred<QueueSendResult>>()
    private val pendingBatch = ConcurrentHashMap<String, CompletableDeferred<List<QueueSendResult>>>()
    private val requestTimestamps = ConcurrentHashMap<String, Long>()

    private val cleaner = StaleRequestCleaner("upstream", logger)

    companion object {
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }

    private fun connect() {
        if (isConnected.get()) return
        synchronized(this) {
            if (isConnected.get()) return
            try {
                cleaner.start(Runnable { cleanupStaleRequests() })

                val responsesObserver = object : StreamObserver<kubemq.Kubemq.QueuesUpstreamResponse> {
                    override fun onNext(response: kubemq.Kubemq.QueuesUpstreamResponse) {
                        val refId = response.refRequestID
                        requestTimestamps.remove(refId)

                        val singleFuture = pendingSingle.remove(refId)
                        if (singleFuture != null) {
                            if (response.isError) {
                                singleFuture.complete(QueueSendResult.error(refId, response.error))
                                return
                            }
                            if (response.resultsCount == 0) {
                                singleFuture.complete(QueueSendResult.error(refId, "no results"))
                                return
                            }
                            singleFuture.complete(QueueSendResult.fromProto(response.getResults(0)))
                            return
                        }

                        val batchFuture = pendingBatch.remove(refId)
                        if (batchFuture != null) {
                            if (response.isError) {
                                batchFuture.complete(listOf(QueueSendResult.error(refId, response.error)))
                                return
                            }
                            batchFuture.complete(response.resultsList.map { QueueSendResult.fromProto(it) })
                        }
                    }

                    override fun onError(t: Throwable) {
                        logger.error({ "Error in upstream response observer: ${t.message}" }, t)
                        closeStreamWithError(t.message ?: "Stream error")
                    }

                    override fun onCompleted() {
                        logger.info { "Upstream response stream completed" }
                        closeStreamWithError("Stream completed")
                    }
                }

                requestsObserver = client.requireTransport().getAsyncStub().queuesUpstream(responsesObserver)
                isConnected.set(true)
            } catch (e: Exception) {
                logger.error({ "Error initializing upstream stream: ${e.message}" }, e)
                isConnected.set(false)
                throw e
            }
        }
    }

    private fun closeStreamWithError(message: String) {
        isConnected.set(false)
        pendingSingle.forEach { (_, future) ->
            future.complete(QueueSendResult.error("", message))
        }
        pendingSingle.clear()
        pendingBatch.forEach { (_, future) ->
            future.complete(listOf(QueueSendResult.error("", message)))
        }
        pendingBatch.clear()
        requestTimestamps.clear()
    }

    private fun cleanupStaleRequests() {
        cleaner.cleanupStaleEntries(pendingSingle, requestTimestamps, QueueSendResult::error)
        cleaner.cleanupStaleEntries(
            pendingBatch,
            requestTimestamps,
            { id, msg -> listOf(QueueSendResult.error(id, msg)) },
        )
    }

    suspend fun sendSingle(message: QueueMessage): QueueSendResult {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<QueueSendResult>()

        pendingSingle[requestId] = deferred
        requestTimestamps[requestId] = System.currentTimeMillis()
        try {
            val request = message.toUpstreamRequest(client.resolvedClientId).toBuilder()
                .setRequestID(requestId)
                .build()
            sendRequest(request)
        } catch (e: Exception) {
            pendingSingle.remove(requestId)
            requestTimestamps.remove(requestId)
            logger.error({ "Error sending queue message: ${e.message}" }, e)
            return QueueSendResult.error(requestId, "Failed to send: ${e.message}")
        }
        return withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
    }

    suspend fun sendBatch(messages: List<QueueMessage>): List<QueueSendResult> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<List<QueueSendResult>>()

        pendingBatch[requestId] = deferred
        requestTimestamps[requestId] = System.currentTimeMillis()
        try {
            val builder = kubemq.Kubemq.QueuesUpstreamRequest.newBuilder()
                .setRequestID(requestId)
            for (msg in messages) {
                builder.addMessages(msg.toProto(client.resolvedClientId))
            }
            sendRequest(builder.build())
        } catch (e: Exception) {
            pendingBatch.remove(requestId)
            requestTimestamps.remove(requestId)
            logger.error({ "Error sending batch queue messages: ${e.message}" }, e)
            return messages.map { QueueSendResult.error(requestId, "Failed to send batch: ${e.message}") }
        }
        return withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
    }

    private fun sendRequest(request: kubemq.Kubemq.QueuesUpstreamRequest) {
        if (!isConnected.get()) {
            connect()
        }
        synchronized(sendLock) {
            val observer = requestsObserver
            if (observer != null) {
                try {
                    observer.onNext(request)
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
