package io.kubemq.sdk.unit.queues

import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.queues.QueueMessagePolicy
import io.kubemq.sdk.queues.QueuesClient
import io.kubemq.sdk.queues.queueMessage
import io.kubemq.sdk.testutil.FakeKubeMQService
import io.kubemq.sdk.testutil.MockGrpcServer
import io.kubemq.sdk.transport.Transport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.withTimeout

class QueuesClientStreamTest : FunSpec({

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

    test("sendQueuesMessage sends single message via stream") {
        val client = createClient()
        val msg = queueMessage {
            channel = "queue-ch"
            body = "hello-stream".toByteArray()
            metadata = "stream-meta"
        }

        val result = withTimeout(10000) { client.sendQueuesMessage(msg) }

        result.isError shouldBe false
        service.capturedUpstreamRequests.size shouldBe 1
        service.capturedUpstreamRequests[0].messagesCount shouldBe 1
        service.capturedUpstreamRequests[0].getMessages(0).channel shouldBe "queue-ch"

        client.close()
    }

    test("sendQueuesMessage validates message") {
        val client = createClient()
        val msg = queueMessage {
            channel = ""
            body = "body".toByteArray()
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendQueuesMessage(msg)
        }
        client.close()
    }

    test("sendQueuesMessages sends batch via stream") {
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
            queueMessage {
                channel = "queue-ch"
                body = "msg3".toByteArray()
            },
        )

        val results = withTimeout(10000) { client.sendQueuesMessages(messages) }

        results.size shouldBe 3
        results.forEach { it.isError shouldBe false }

        client.close()
    }

    test("sendQueuesMessages with empty list throws") {
        val client = createClient()

        shouldThrow<KubeMQException.Validation> {
            client.sendQueuesMessages(emptyList())
        }
        client.close()
    }

    test("sendQueuesMessage with policy sends policy in proto") {
        val client = createClient()
        val msg = queueMessage {
            channel = "queue-ch"
            body = "body".toByteArray()
            policy = QueueMessagePolicy(
                expirationSeconds = 60,
                delaySeconds = 5,
                maxReceiveCount = 3,
                maxReceiveQueue = "dead-letter",
            )
        }

        val result = withTimeout(10000) { client.sendQueuesMessage(msg) }
        result.isError shouldBe false

        service.capturedUpstreamRequests.size shouldBe 1
        val sentMsg = service.capturedUpstreamRequests[0].getMessages(0)
        sentMsg.policy.expirationSeconds shouldBe 60
        sentMsg.policy.delaySeconds shouldBe 5
        sentMsg.policy.maxReceiveCount shouldBe 3
        sentMsg.policy.maxReceiveQueue shouldBe "dead-letter"

        client.close()
    }

    test("receiveQueuesMessages polls via stream") {
        val client = createClient()

        val response = withTimeout(15000) {
            client.receiveQueuesMessages {
                channel = "queue-ch"
                maxItems = 10
                waitTimeoutMs = 3000
            }
        }

        response.isError shouldBe false
        service.capturedDownstreamRequests.size shouldBe 1
        service.capturedDownstreamRequests[0].channel shouldBe "queue-ch"
        service.capturedDownstreamRequests[0].maxItems shouldBe 10
        service.capturedDownstreamRequests[0].waitTimeout shouldBe 3000

        client.close()
    }

    test("createQueuesChannel and deleteQueuesChannel send correct requests") {
        val client = createClient()

        client.createQueuesChannel("my-queue")
        service.capturedRequests.size shouldBe 1
        service.capturedRequests[0].tagsMap["channel_type"] shouldBe "queues"
        service.capturedRequests[0].tagsMap["channel"] shouldBe "my-queue"
        service.capturedRequests[0].metadata shouldBe "create-channel"

        client.deleteQueuesChannel("my-queue")
        service.capturedRequests.size shouldBe 2
        service.capturedRequests[1].metadata shouldBe "delete-channel"

        client.close()
    }

    test("sendQueuesMessage validates body or metadata or tags required") {
        val client = createClient()
        val msg = queueMessage {
            channel = "queue-ch"
            // no body, no metadata, no tags
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendQueuesMessage(msg)
        }
        client.close()
    }
})
