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
import io.kubemq.sdk.pubsub.StartPosition
import io.kubemq.sdk.pubsub.eventStoreMessage
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

class PubSubClientStoreTest : FunSpec({

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

    test("publishEventStore sends event with store=true") {
        val client = createClient()
        val event = eventStoreMessage {
            channel = "events-store-ch"
            metadata = "store-meta"
            body = "store-body".toByteArray()
        }

        val result = client.publishEventStore(event)
        // EventStreamHelper uses sendEventsStream bidi stream; await completes when result arrives
        delay(200) // ensure bidi stream processing completes

        service.capturedEvents.size shouldBe 1
        val captured = service.capturedEvents[0]
        captured.channel shouldBe "events-store-ch"
        captured.metadata shouldBe "store-meta"
        captured.store shouldBe true
        captured.body.toStringUtf8() shouldBe "store-body"

        result.sent shouldBe true

        client.close()
    }

    test("publishEventStore validates channel - no wildcards") {
        val client = createClient()
        val event = eventStoreMessage {
            channel = "events.*"
            metadata = "meta"
        }

        shouldThrow<KubeMQException.Validation> {
            client.publishEventStore(event)
        }
        client.close()
    }

    test("publishEventStore validates body or metadata") {
        val client = createClient()
        val event = eventStoreMessage {
            channel = "events-store-ch"
            metadata = ""
            body = ByteArray(0)
        }

        shouldThrow<KubeMQException.Validation> {
            client.publishEventStore(event)
        }
        client.close()
    }

    test("subscribeToEventsStore with StartNewOnly") {
        val client = createClient()

        service.eventsToEmit = listOf(
            kubemq.Kubemq.EventReceive.newBuilder()
                .setEventID("store-1")
                .setChannel("events-store-ch")
                .setMetadata("meta")
                .setBody(ByteString.copyFromUtf8("body"))
                .setTimestamp(1000L)
                .setSequence(10L)
                .build(),
        )

        val events = withTimeout(5000) {
            client.subscribeToEventsStore {
                channel = "events-store-ch"
                startPosition = StartPosition.StartNewOnly
            }.take(1).toList()
        }

        events.size shouldBe 1
        events[0].id shouldBe "store-1"
        events[0].sequence shouldBe 10L

        service.capturedSubscriptions.size shouldBe 1
        val sub = service.capturedSubscriptions[0]
        sub.subscribeTypeData shouldBe kubemq.Kubemq.Subscribe.SubscribeType.EventsStore
        sub.eventsStoreTypeData shouldBe kubemq.Kubemq.Subscribe.EventsStoreType.StartNewOnly

        client.close()
    }

    test("subscribeToEventsStore with StartFromFirst") {
        val client = createClient()
        service.eventsToEmit = listOf(
            kubemq.Kubemq.EventReceive.newBuilder()
                .setEventID("first-1")
                .setChannel("events-store-ch")
                .setMetadata("m")
                .setBody(ByteString.copyFromUtf8("b"))
                .setTimestamp(1L)
                .setSequence(1L)
                .build(),
        )

        withTimeout(5000) {
            client.subscribeToEventsStore {
                channel = "events-store-ch"
                startPosition = StartPosition.StartFromFirst
            }.take(1).toList()
        }

        val sub = service.capturedSubscriptions[0]
        sub.eventsStoreTypeData shouldBe kubemq.Kubemq.Subscribe.EventsStoreType.StartFromFirst

        client.close()
    }

    test("subscribeToEventsStore with StartAtSequence") {
        val client = createClient()
        service.eventsToEmit = listOf(
            kubemq.Kubemq.EventReceive.newBuilder()
                .setEventID("seq-1")
                .setChannel("events-store-ch")
                .setMetadata("m")
                .setBody(ByteString.copyFromUtf8("b"))
                .setTimestamp(1L)
                .setSequence(42L)
                .build(),
        )

        withTimeout(5000) {
            client.subscribeToEventsStore {
                channel = "events-store-ch"
                startPosition = StartPosition.StartAtSequence(42)
            }.take(1).toList()
        }

        val sub = service.capturedSubscriptions[0]
        sub.eventsStoreTypeData shouldBe kubemq.Kubemq.Subscribe.EventsStoreType.StartAtSequence
        sub.eventsStoreTypeValue shouldBe 42L

        client.close()
    }

    test("subscribeToEventsStore validates channel - no wildcards") {
        val client = createClient()

        shouldThrow<KubeMQException.Validation> {
            client.subscribeToEventsStore {
                channel = "events.*"
            }
        }
        client.close()
    }

    test("createEventsStoreChannel sends correct request") {
        val client = createClient()
        client.createEventsStoreChannel("my-store-ch")

        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.tagsMap["channel_type"] shouldBe "events_store"
        req.tagsMap["channel"] shouldBe "my-store-ch"

        client.close()
    }
})
