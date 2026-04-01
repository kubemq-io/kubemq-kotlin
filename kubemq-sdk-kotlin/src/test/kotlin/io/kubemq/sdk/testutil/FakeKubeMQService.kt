package io.kubemq.sdk.testutil

import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * A fake gRPC service implementing the KubeMQ coroutine-based server API.
 * Supports configurable responses, request capture, and error injection.
 */
class FakeKubeMQService : kubemq.kubemqGrpcKt.kubemqCoroutineImplBase() {

    // --- Captured requests for verification ---
    val capturedEvents = CopyOnWriteArrayList<kubemq.Kubemq.Event>()
    val capturedRequests = CopyOnWriteArrayList<kubemq.Kubemq.Request>()
    val capturedResponses = CopyOnWriteArrayList<kubemq.Kubemq.Response>()
    val capturedSubscriptions = CopyOnWriteArrayList<kubemq.Kubemq.Subscribe>()
    val capturedQueueMessages = CopyOnWriteArrayList<kubemq.Kubemq.QueueMessage>()
    val capturedQueueBatchRequests = CopyOnWriteArrayList<kubemq.Kubemq.QueueMessagesBatchRequest>()
    val capturedReceiveQueueRequests = CopyOnWriteArrayList<kubemq.Kubemq.ReceiveQueueMessagesRequest>()
    val capturedAckAllRequests = CopyOnWriteArrayList<kubemq.Kubemq.AckAllQueueMessagesRequest>()
    val capturedUpstreamRequests = CopyOnWriteArrayList<kubemq.Kubemq.QueuesUpstreamRequest>()
    val capturedDownstreamRequests = CopyOnWriteArrayList<kubemq.Kubemq.QueuesDownstreamRequest>()

    // --- Configurable responses ---
    var pingResponse: kubemq.Kubemq.PingResult = kubemq.Kubemq.PingResult.newBuilder()
        .setHost("localhost")
        .setVersion("2.5.0")
        .setServerStartTime(1000000L)
        .setServerUpTimeSeconds(3600L)
        .build()

    var sendEventResponse: kubemq.Kubemq.Result = kubemq.Kubemq.Result.newBuilder()
        .setSent(true)
        .build()

    var sendRequestResponse: AtomicReference<kubemq.Kubemq.Response> = AtomicReference(
        kubemq.Kubemq.Response.newBuilder()
            .setRequestID("test-request-id")
            .setExecuted(true)
            .setTimestamp(System.currentTimeMillis() * 1_000_000L)
            .build()
    )

    var sendQueueMessageResponse: kubemq.Kubemq.SendQueueMessageResult =
        kubemq.Kubemq.SendQueueMessageResult.newBuilder()
            .setMessageID("msg-1")
            .setSentAt(System.currentTimeMillis() * 1_000_000L)
            .setIsError(false)
            .build()

    var sendQueueMessagesBatchResponse: kubemq.Kubemq.QueueMessagesBatchResponse =
        kubemq.Kubemq.QueueMessagesBatchResponse.newBuilder()
            .setBatchID("batch-1")
            .build()

    var receiveQueueMessagesResponse: kubemq.Kubemq.ReceiveQueueMessagesResponse =
        kubemq.Kubemq.ReceiveQueueMessagesResponse.newBuilder()
            .setRequestID("req-1")
            .setIsError(false)
            .setMessagesReceived(0)
            .setMessagesExpired(0)
            .build()

    var ackAllQueueMessagesResponse: kubemq.Kubemq.AckAllQueueMessagesResponse =
        kubemq.Kubemq.AckAllQueueMessagesResponse.newBuilder()
            .setRequestID("req-1")
            .setAffectedMessages(0)
            .setIsError(false)
            .build()

    // Events to emit for subscriptions
    var eventsToEmit: List<kubemq.Kubemq.EventReceive> = emptyList()
    var requestsToEmit: List<kubemq.Kubemq.Request> = emptyList()

    // Error injection
    var errorToThrow: Exception? = null

    // --- RPC Implementations ---

    override suspend fun ping(request: kubemq.Kubemq.Empty): kubemq.Kubemq.PingResult {
        errorToThrow?.let { throw it }
        return pingResponse
    }

    override suspend fun sendEvent(request: kubemq.Kubemq.Event): kubemq.Kubemq.Result {
        errorToThrow?.let { throw it }
        capturedEvents.add(request)
        return sendEventResponse.toBuilder()
            .setEventID(request.eventID)
            .build()
    }

    override fun sendEventsStream(requests: Flow<kubemq.Kubemq.Event>): Flow<kubemq.Kubemq.Result> {
        return requests.map { event ->
            errorToThrow?.let { throw it }
            capturedEvents.add(event)
            kubemq.Kubemq.Result.newBuilder()
                .setEventID(event.eventID)
                .setSent(true)
                .build()
        }
    }

    override fun subscribeToEvents(request: kubemq.Kubemq.Subscribe): Flow<kubemq.Kubemq.EventReceive> {
        capturedSubscriptions.add(request)
        errorToThrow?.let { throw it }
        return flow {
            for (event in eventsToEmit) {
                emit(event)
            }
        }
    }

    override fun subscribeToRequests(request: kubemq.Kubemq.Subscribe): Flow<kubemq.Kubemq.Request> {
        capturedSubscriptions.add(request)
        errorToThrow?.let { throw it }
        return flow {
            for (req in requestsToEmit) {
                emit(req)
            }
        }
    }

    override suspend fun sendRequest(request: kubemq.Kubemq.Request): kubemq.Kubemq.Response {
        errorToThrow?.let { throw it }
        capturedRequests.add(request)
        return sendRequestResponse.get().toBuilder()
            .setRequestID(request.requestID)
            .build()
    }

    override suspend fun sendResponse(request: kubemq.Kubemq.Response): kubemq.Kubemq.Empty {
        errorToThrow?.let { throw it }
        capturedResponses.add(request)
        return kubemq.Kubemq.Empty.getDefaultInstance()
    }

    override suspend fun sendQueueMessage(request: kubemq.Kubemq.QueueMessage): kubemq.Kubemq.SendQueueMessageResult {
        errorToThrow?.let { throw it }
        capturedQueueMessages.add(request)
        return sendQueueMessageResponse.toBuilder()
            .setMessageID(request.messageID)
            .build()
    }

    override suspend fun sendQueueMessagesBatch(request: kubemq.Kubemq.QueueMessagesBatchRequest): kubemq.Kubemq.QueueMessagesBatchResponse {
        errorToThrow?.let { throw it }
        capturedQueueBatchRequests.add(request)
        val builder = sendQueueMessagesBatchResponse.toBuilder()
            .setBatchID(request.batchID)
        request.messagesList.forEach { msg ->
            builder.addResults(
                kubemq.Kubemq.SendQueueMessageResult.newBuilder()
                    .setMessageID(msg.messageID)
                    .setSentAt(System.currentTimeMillis() * 1_000_000L)
                    .setIsError(false)
                    .build()
            )
        }
        return builder.build()
    }

    override suspend fun receiveQueueMessages(request: kubemq.Kubemq.ReceiveQueueMessagesRequest): kubemq.Kubemq.ReceiveQueueMessagesResponse {
        errorToThrow?.let { throw it }
        capturedReceiveQueueRequests.add(request)
        return receiveQueueMessagesResponse.toBuilder()
            .setRequestID(request.requestID)
            .build()
    }

    override suspend fun ackAllQueueMessages(request: kubemq.Kubemq.AckAllQueueMessagesRequest): kubemq.Kubemq.AckAllQueueMessagesResponse {
        errorToThrow?.let { throw it }
        capturedAckAllRequests.add(request)
        return ackAllQueueMessagesResponse.toBuilder()
            .setRequestID(request.requestID)
            .build()
    }

    override fun queuesUpstream(requests: Flow<kubemq.Kubemq.QueuesUpstreamRequest>): Flow<kubemq.Kubemq.QueuesUpstreamResponse> {
        return requests.map { req ->
            errorToThrow?.let { throw it }
            capturedUpstreamRequests.add(req)
            val resultBuilder = kubemq.Kubemq.QueuesUpstreamResponse.newBuilder()
                .setRefRequestID(req.requestID)
                .setIsError(false)
            req.messagesList.forEach { msg ->
                resultBuilder.addResults(
                    kubemq.Kubemq.SendQueueMessageResult.newBuilder()
                        .setMessageID(msg.messageID)
                        .setSentAt(System.currentTimeMillis() * 1_000_000L)
                        .setIsError(false)
                        .build()
                )
            }
            resultBuilder.build()
        }
    }

    override fun queuesDownstream(requests: Flow<kubemq.Kubemq.QueuesDownstreamRequest>): Flow<kubemq.Kubemq.QueuesDownstreamResponse> {
        return requests.map { req ->
            errorToThrow?.let { throw it }
            capturedDownstreamRequests.add(req)
            kubemq.Kubemq.QueuesDownstreamResponse.newBuilder()
                .setRefRequestId(req.requestID)
                .setTransactionId("tx-${req.requestID}")
                .setIsError(false)
                .setTransactionComplete(false)
                .build()
        }
    }

    fun reset() {
        capturedEvents.clear()
        capturedRequests.clear()
        capturedResponses.clear()
        capturedSubscriptions.clear()
        capturedQueueMessages.clear()
        capturedQueueBatchRequests.clear()
        capturedReceiveQueueRequests.clear()
        capturedAckAllRequests.clear()
        capturedUpstreamRequests.clear()
        capturedDownstreamRequests.clear()
        errorToThrow = null
        eventsToEmit = emptyList()
        requestsToEmit = emptyList()
    }
}
