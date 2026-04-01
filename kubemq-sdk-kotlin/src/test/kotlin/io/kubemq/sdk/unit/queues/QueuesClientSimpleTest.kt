package io.kubemq.sdk.unit.queues

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.queues.QueuesClient
import io.kubemq.sdk.queues.queueMessage
import io.kubemq.sdk.testutil.FakeKubeMQService
import io.kubemq.sdk.testutil.MockGrpcServer
import io.kubemq.sdk.transport.Transport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class QueuesClientSimpleTest : FunSpec({

    lateinit var service: FakeKubeMQService
    lateinit var server: MockGrpcServer

    beforeEach {
        service = FakeKubeMQService()
        server = MockGrpcServer.create(service)
    }

    afterEach {
        server.close()
    }

    fun createClient(): QueuesClient {
        val client = KubeMQClient.queues {
            address = "localhost:50000"
            clientId = "test-client"
        } as QueuesClient

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

    test("sendQueueMessage sends message via simple API") {
        val client = createClient()
        val msg = queueMessage {
            channel = "queue-ch"
            body = "queue-body".toByteArray()
        }

        val result = client.sendQueueMessage(msg)

        result.isError shouldBe false
        service.capturedQueueMessages.size shouldBe 1
        service.capturedQueueMessages[0].channel shouldBe "queue-ch"
        service.capturedQueueMessages[0].body.toStringUtf8() shouldBe "queue-body"

        client.close()
    }

    test("sendQueueMessage validates channel") {
        val client = createClient()
        val msg = queueMessage {
            channel = ""
            body = "body".toByteArray()
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendQueueMessage(msg)
        }
        client.close()
    }

    test("sendQueueMessagesBatch sends multiple messages") {
        val client = createClient()
        val messages = listOf(
            queueMessage {
                channel = "queue-ch"
                body = "msg1".toByteArray()
            },
            queueMessage {
                channel = "queue-ch"
                body = "msg2".toByteArray()
            },
        )

        val results = client.sendQueueMessagesBatch(messages)

        results.size shouldBe 2
        results.forEach { it.isError shouldBe false }
        service.capturedQueueBatchRequests.size shouldBe 1
        service.capturedQueueBatchRequests[0].messagesCount shouldBe 2

        client.close()
    }

    test("sendQueueMessagesBatch with empty list throws") {
        val client = createClient()

        shouldThrow<KubeMQException.Validation> {
            client.sendQueueMessagesBatch(emptyList())
        }
        client.close()
    }

    test("receiveQueueMessages sends correct receive request") {
        val client = createClient()

        val response = client.receiveQueueMessages {
            channel = "queue-ch"
            maxNumberOfMessages = 5
            waitTimeSeconds = 2
        }

        response.isError shouldBe false
        service.capturedReceiveQueueRequests.size shouldBe 1
        service.capturedReceiveQueueRequests[0].channel shouldBe "queue-ch"
        service.capturedReceiveQueueRequests[0].maxNumberOfMessages shouldBe 5
        service.capturedReceiveQueueRequests[0].waitTimeSeconds shouldBe 2
        service.capturedReceiveQueueRequests[0].isPeak shouldBe false

        client.close()
    }

    test("peekQueueMessages sends peek request") {
        val client = createClient()

        val response = client.peekQueueMessages {
            channel = "queue-ch"
            maxNumberOfMessages = 3
            waitTimeSeconds = 1
        }

        response.isError shouldBe false
        service.capturedReceiveQueueRequests.size shouldBe 1
        service.capturedReceiveQueueRequests[0].channel shouldBe "queue-ch"
        service.capturedReceiveQueueRequests[0].isPeak shouldBe true

        client.close()
    }
})
