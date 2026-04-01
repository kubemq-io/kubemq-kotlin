package io.kubemq.sdk.pubsub

import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.exception.KubeMQException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class EventStreamHelper {
    @Volatile                                                                      // FIX-2: visible to gRPC threads
    private var sendObserver: io.grpc.stub.StreamObserver<kubemq.Kubemq.Event>? = null
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<EventSendResult>>()
    private val requestTimestamps = ConcurrentHashMap<String, Long>()
    private val grpcMutex = Mutex()                                                // M-2: replaced grpcLock
    private var cleanupJob: Job? = null

    suspend fun sendEvent(client: KubeMQClient, event: kubemq.Kubemq.Event) {
        grpcMutex.withLock {                                                       // M-2
            if (sendObserver == null) {
                sendObserver = client.requireTransport().getAsyncStub()
                    .sendEventsStream(createResultObserver())
            }
            sendObserver?.onNext(event)
                ?: throw KubeMQException.StreamBroken("Event stream not available", operation = "publishEvent")
        }
    }

    suspend fun sendEventStore(client: KubeMQClient, event: kubemq.Kubemq.Event): EventSendResult {
        val requestId = event.eventID.ifBlank { UUID.randomUUID().toString() }
        val eventWithId = if (event.eventID != requestId) {
            event.toBuilder().setEventID(requestId).build()
        } else {
            event
        }
        val deferred = CompletableDeferred<EventSendResult>()

        grpcMutex.withLock {                                                       // M-2
            if (sendObserver == null) {
                sendObserver = client.requireTransport().getAsyncStub()
                    .sendEventsStream(createResultObserver())
                startCleanup(client.scope)
            }
            pendingResponses[requestId] = deferred
            requestTimestamps[requestId] = System.currentTimeMillis()
            sendObserver?.onNext(eventWithId)
                ?: throw KubeMQException.StreamBroken("Event stream not available", operation = "publishEventStore")
        }

        return deferred.await()
    }

    private fun startCleanup(scope: CoroutineScope) {
        if (cleanupJob?.isActive == true) return
        cleanupJob = scope.launch {
            while (isActive) {
                delay(30_000)
                val now = System.currentTimeMillis()
                requestTimestamps.forEach { (id, ts) ->
                    if (now - ts > 60_000) {
                        requestTimestamps.remove(id)
                        pendingResponses.remove(id)?.complete(
                            EventSendResult(id = id, sent = false, error = "Request timed out (60s)")
                        )
                    }
                }
            }
        }
    }

    private fun createResultObserver(): io.grpc.stub.StreamObserver<kubemq.Kubemq.Result> {
        return object : io.grpc.stub.StreamObserver<kubemq.Kubemq.Result> {
            override fun onNext(result: kubemq.Kubemq.Result) {
                requestTimestamps.remove(result.eventID)
                val deferred = pendingResponses.remove(result.eventID)
                deferred?.complete(EventSendResult.decode(result))
            }

            override fun onError(t: Throwable) {
                // FIX-2: onError is called from gRPC executor thread (not a coroutine).
                // sendObserver is @Volatile so the null write is immediately visible to
                // coroutine threads holding grpcMutex. The send paths use ?. (safe call)
                // after acquiring the mutex, so a concurrent null from onError is safe.
                sendObserver = null
                pendingResponses.forEach { (_, d) ->
                    d.complete(EventSendResult(id = "", sent = false, error = t.message ?: "Stream error"))
                }
                pendingResponses.clear()
                requestTimestamps.clear()
            }

            override fun onCompleted() {
                sendObserver = null  // @Volatile ensures visibility (FIX-2)
            }
        }
    }

    fun close() {
        cleanupJob?.cancel()
        // Use runBlocking-free close since this is called from non-suspend context
        try { sendObserver?.onCompleted() } catch (_: Exception) {}
        sendObserver = null
        pendingResponses.clear()
        requestTimestamps.clear()
    }
}
