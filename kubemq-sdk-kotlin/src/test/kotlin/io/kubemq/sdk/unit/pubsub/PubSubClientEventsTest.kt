package io.kubemq.sdk.unit.pubsub

import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.pubsub.PubSubClient
import io.kubemq.sdk.pubsub.eventMessage
import io.kubemq.sdk.testutil.FakeKubeMQService
import io.kubemq.sdk.testutil.MockGrpcServer
import io.kubemq.sdk.transport.Transport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout

class PubSubClientEventsTest : FunSpec({

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

        // Inject transport pointing to in-process server
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

    test("publishEvent sends event to server") {
        val client = createClient()
        val event = eventMessage {
            channel = "events-channel"
            metadata = "test-meta"
            body = "hello".toByteArray()
            tags = mapOf("key" to "value")
        }

        client.publishEvent(event)

        // The bidi stream event capture is asynchronous; wait briefly
        delay(200)

        service.capturedEvents.size shouldBe 1
        val captured = service.capturedEvents[0]
        captured.channel shouldBe "events-channel"
        captured.metadata shouldBe "test-meta"
        captured.body.toStringUtf8() shouldBe "hello"
        captured.tagsMap["key"] shouldBe "value"
        captured.store shouldBe false

        client.close()
    }

    test("publishEvent validates channel") {
        val client = createClient()
        val event = eventMessage {
            channel = ""
            metadata = "meta"
        }

        shouldThrow<KubeMQException.Validation> {
            client.publishEvent(event)
        }
        client.close()
    }

    test("publishEvent validates body or metadata") {
        val client = createClient()
        val event = eventMessage {
            channel = "events-channel"
            metadata = ""
            body = ByteArray(0)
        }

        shouldThrow<KubeMQException.Validation> {
            client.publishEvent(event)
        }
        client.close()
    }

    test("subscribeToEvents receives events from server") {
        val client = createClient()

        service.eventsToEmit = listOf(
            kubemq.Kubemq.EventReceive.newBuilder()
                .setEventID("evt-1")
                .setChannel("events-channel")
                .setMetadata("meta1")
                .setBody(ByteString.copyFromUtf8("body1"))
                .setTimestamp(1000L)
                .setSequence(1L)
                .putTags("x-kubemq-client-id", "sender-1")
                .build(),
            kubemq.Kubemq.EventReceive.newBuilder()
                .setEventID("evt-2")
                .setChannel("events-channel")
                .setMetadata("meta2")
                .setBody(ByteString.copyFromUtf8("body2"))
                .setTimestamp(2000L)
                .setSequence(2L)
                .putTags("x-kubemq-client-id", "sender-2")
                .build(),
        )

        val events = withTimeout(5000) {
            client.subscribeToEvents {
                channel = "events-channel"
                group = "test-group"
            }.take(2).toList()
        }

        events.size shouldBe 2
        events[0].id shouldBe "evt-1"
        events[0].channel shouldBe "events-channel"
        events[0].metadata shouldBe "meta1"
        String(events[0].body) shouldBe "body1"
        events[0].fromClientId shouldBe "sender-1"

        events[1].id shouldBe "evt-2"
        events[1].sequence shouldBe 2L

        // Verify subscription was sent correctly
        service.capturedSubscriptions.size shouldBe 1
        service.capturedSubscriptions[0].channel shouldBe "events-channel"
        service.capturedSubscriptions[0].group shouldBe "test-group"

        client.close()
    }

    test("subscribeToEvents validates channel") {
        val client = createClient()

        shouldThrow<KubeMQException.Validation> {
            client.subscribeToEvents {
                channel = ""
            }
        }
        client.close()
    }

    test("createEventsChannel sends correct request") {
        val client = createClient()
        client.createEventsChannel("my-events-ch")

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.tagsMap["channel_type"] shouldBe "events"
        req.tagsMap["channel"] shouldBe "my-events-ch"
        req.metadata shouldBe "create-channel"

        client.close()
    }

    test("deleteEventsChannel sends correct request") {
        val client = createClient()
        client.deleteEventsChannel("my-events-ch")

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.tagsMap["channel_type"] shouldBe "events"
        req.tagsMap["channel"] shouldBe "my-events-ch"
        req.metadata shouldBe "delete-channel"

        client.close()
    }

    test("listEventsChannels sends correct request") {
        val client = createClient()

        // Set up response with an executed=true result
        service.sendRequestResponse.set(
            kubemq.Kubemq.Response.newBuilder()
                .setRequestID("test")
                .setExecuted(true)
                .setBody(ByteString.copyFromUtf8("[]"))
                .build()
        )

        val channels = client.listEventsChannels("search-term")

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.tagsMap["channel_type"] shouldBe "events"
        req.tagsMap["channel_search"] shouldBe "search-term"

        channels shouldBe emptyList()

        client.close()
    }
})
