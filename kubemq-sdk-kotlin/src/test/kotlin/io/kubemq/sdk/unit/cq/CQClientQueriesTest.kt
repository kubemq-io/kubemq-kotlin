package io.kubemq.sdk.unit.cq

import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.cq.CQClient
import io.kubemq.sdk.cq.QueryReceived
import io.kubemq.sdk.cq.queryMessage
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.testutil.FakeKubeMQService
import io.kubemq.sdk.testutil.MockGrpcServer
import io.kubemq.sdk.transport.Transport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList

class CQClientQueriesTest : FunSpec({

    lateinit var service: FakeKubeMQService
    lateinit var server: MockGrpcServer

    beforeEach {
        service = FakeKubeMQService()
        server = MockGrpcServer.create(service)
    }

    afterEach {
        server.close()
    }

    fun createClient(): CQClient {
        val client = KubeMQClient.cq {
            address = "localhost:50000"
            clientId = "test-client"
        } as CQClient

        val stub = kubemq.kubemqGrpcKt.kubemqCoroutineStub(server.channel)
        val asyncStub = kubemq.kubemqGrpc.newStub(server.channel)
        val transport = mockk<Transport>()
        every { transport.getUnaryStub() } returns stub
        every { transport.getStreamingStub() } returns stub
        every { transport.getAsyncStub() } returns asyncStub
        coEvery { transport.ping() } returns ServerInfo("localhost", "2.5.0", 1000000L, 3600L)
        every { transport.isReady } returns true
        every { transport.close() } returns Unit

        client.transport = transport
        client.stateMachine.transitionTo(ConnectionState.Connecting)
        client.stateMachine.transitionTo(ConnectionState.Ready)
        return client
    }

    test("sendQuery sends query and receives response") {
        val client = createClient()

        service.sendRequestResponse.set(
            kubemq.Kubemq.Response.newBuilder()
                .setRequestID("qry-1")
                .setExecuted(true)
                .setMetadata("result-meta")
                .setBody(ByteString.copyFromUtf8("result-body"))
                .setCacheHit(false)
                .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                .putTags("result-key", "result-value")
                .build()
        )

        val query = queryMessage {
            channel = "queries-ch"
            metadata = "get-data"
            body = "query-body".toByteArray()
            timeoutMs = 5000
        }

        val response = client.sendQuery(query)

        response.executed shouldBe true
        response.metadata shouldBe "result-meta"
        String(response.body) shouldBe "result-body"
        response.cacheHit shouldBe false
        response.tags["result-key"] shouldBe "result-value"

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.channel shouldBe "queries-ch"
        req.requestTypeData shouldBe kubemq.Kubemq.Request.RequestType.Query
        req.timeout shouldBe 5000

        client.close()
    }

    test("sendQuery with cache key") {
        val client = createClient()

        service.sendRequestResponse.set(
            kubemq.Kubemq.Response.newBuilder()
                .setRequestID("qry-cache")
                .setExecuted(true)
                .setCacheHit(true)
                .setBody(ByteString.copyFromUtf8("cached"))
                .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                .build()
        )

        val query = queryMessage {
            channel = "queries-ch"
            metadata = "cached-query"
            timeoutMs = 5000
            cacheKey = "my-cache-key"
            cacheTtlSeconds = 60
        }

        val response = client.sendQuery(query)
        response.cacheHit shouldBe true

        val req = service.capturedRequests[0]
        req.cacheKey shouldBe "my-cache-key"
        req.cacheTTL shouldBe 60 // C-3: proto field is in seconds, no x1000 multiplier

        client.close()
    }

    test("sendQuery validates channel") {
        val client = createClient()
        val query = queryMessage {
            channel = ""
            timeoutMs = 1000
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendQuery(query)
        }
        client.close()
    }

    test("subscribeToQueries receives queries from server") {
        val client = createClient()

        service.requestsToEmit = listOf(
            kubemq.Kubemq.Request.newBuilder()
                .setRequestID("qry-recv-1")
                .setChannel("queries-ch")
                .setMetadata("fetch")
                .setBody(ByteString.copyFromUtf8("query-data"))
                .setReplyChannel("reply-ch")
                .putTags("x-kubemq-client-id", "sender-1")
                .build(),
        )

        val queries = client.subscribeToQueries {
            channel = "queries-ch"
            group = "qry-group"
        }.toList()

        queries.size shouldBe 1
        queries[0].id shouldBe "qry-recv-1"
        queries[0].channel shouldBe "queries-ch"
        queries[0].metadata shouldBe "fetch"
        String(queries[0].body) shouldBe "query-data"
        queries[0].replyChannel shouldBe "reply-ch"

        service.capturedSubscriptions.size shouldBe 1
        service.capturedSubscriptions[0].subscribeTypeData shouldBe kubemq.Kubemq.Subscribe.SubscribeType.Queries

        client.close()
    }

    test("sendQueryResponse sends response to server") {
        val client = createClient()

        val received = QueryReceived(
            id = "qry-1",
            channel = "queries-ch",
            metadata = "fetch",
            body = ByteArray(0),
            replyChannel = "reply-ch",
            tags = emptyMap(),
        )

        val responseMsg = received.respond {
            executed = true
            metadata = "result"
            body = "response-data".toByteArray()
            tags = mapOf("resp-key" to "resp-value")
        }

        client.sendQueryResponse(responseMsg)

        service.capturedResponses.size shouldBe 1
        service.capturedResponses[0].requestID shouldBe "qry-1"
        service.capturedResponses[0].replyChannel shouldBe "reply-ch"
        service.capturedResponses[0].executed shouldBe true
        service.capturedResponses[0].metadata shouldBe "result"
        service.capturedResponses[0].body.toStringUtf8() shouldBe "response-data"
        service.capturedResponses[0].tagsMap["resp-key"] shouldBe "resp-value"

        client.close()
    }

    test("sendQueryResponse with blank replyChannel throws") {
        val client = createClient()

        val responseMsg = QueryReceived(
            id = "qry-1",
            channel = "queries-ch",
            metadata = "fetch",
            body = ByteArray(0),
            replyChannel = "",
            tags = emptyMap(),
        ).respond {
            executed = true
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendQueryResponse(responseMsg)
        }
        client.close()
    }
})
