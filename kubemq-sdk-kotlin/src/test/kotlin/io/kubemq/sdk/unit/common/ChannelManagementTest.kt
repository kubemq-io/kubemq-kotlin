package io.kubemq.sdk.unit.common

import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.pubsub.PubSubClient
import io.kubemq.sdk.testutil.FakeKubeMQService
import io.kubemq.sdk.testutil.MockGrpcServer
import io.kubemq.sdk.transport.Transport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class ChannelManagementTest : FunSpec({

    lateinit var service: FakeKubeMQService
    lateinit var server: MockGrpcServer

    beforeEach {
        service = FakeKubeMQService()
        server = MockGrpcServer.create(service)
    }

    afterEach {
        server.close()
    }

    fun createClient(): PubSubClient {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
            clientId = "test-client"
        } as PubSubClient

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

    test("createEventsChannel sends create-channel request with events type") {
        val client = createClient()
        client.createEventsChannel("my-events-ch")

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.metadata shouldBe "create-channel"
        req.tagsMap["channel_type"] shouldBe "events"
        req.tagsMap["channel"] shouldBe "my-events-ch"
        req.tagsMap["client_id"] shouldBe "test-client"
        req.requestTypeData shouldBe kubemq.Kubemq.Request.RequestType.Query

        client.close()
    }

    test("deleteEventsStoreChannel sends delete-channel request with events_store type") {
        val client = createClient()
        client.deleteEventsStoreChannel("my-store-ch")

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.metadata shouldBe "delete-channel"
        req.tagsMap["channel_type"] shouldBe "events_store"
        req.tagsMap["channel"] shouldBe "my-store-ch"

        client.close()
    }

    test("listEventsStoreChannels sends list-channels request and parses empty result") {
        val client = createClient()

        service.sendRequestResponse.set(
            kubemq.Kubemq.Response.newBuilder()
                .setRequestID("test")
                .setExecuted(true)
                .setBody(ByteString.copyFromUtf8("[]"))
                .build()
        )

        val channels = client.listEventsStoreChannels("search-prefix")

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.metadata shouldBe "list-channels"
        req.tagsMap["channel_type"] shouldBe "events_store"
        req.tagsMap["channel_search"] shouldBe "search-prefix"

        channels shouldBe emptyList()

        client.close()
    }

    test("createChannel validates channel name - blank throws") {
        val client = createClient()

        shouldThrow<KubeMQException.Validation> {
            client.createEventsChannel("")
        }
        client.close()
    }
})
