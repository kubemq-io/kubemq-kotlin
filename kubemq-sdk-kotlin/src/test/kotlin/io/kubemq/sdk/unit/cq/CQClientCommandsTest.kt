package io.kubemq.sdk.unit.cq

import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.cq.CQClient
import io.kubemq.sdk.cq.CommandReceived
import io.kubemq.sdk.cq.commandMessage
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.testutil.FakeKubeMQService
import io.kubemq.sdk.testutil.MockGrpcServer
import io.kubemq.sdk.transport.Transport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList

class CQClientCommandsTest : FunSpec({

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

    test("sendCommand sends command and receives response") {
        val client = createClient()

        service.sendRequestResponse.set(
            kubemq.Kubemq.Response.newBuilder()
                .setRequestID("cmd-1")
                .setExecuted(true)
                .setTimestamp(System.currentTimeMillis() * 1_000_000L)
                .build()
        )

        val cmd = commandMessage {
            channel = "commands-ch"
            metadata = "do-something"
            body = "cmd-body".toByteArray()
            timeoutMs = 5000
        }

        val response = client.sendCommand(cmd)

        response.executed shouldBe true
        service.capturedRequests.size shouldBe 1
        val req = service.capturedRequests[0]
        req.channel shouldBe "commands-ch"
        req.metadata shouldBe "do-something"
        req.requestTypeData shouldBe kubemq.Kubemq.Request.RequestType.Command
        req.timeout shouldBe 5000

        client.close()
    }

    test("sendCommand validates channel") {
        val client = createClient()
        val cmd = commandMessage {
            channel = ""
            timeoutMs = 1000
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendCommand(cmd)
        }
        client.close()
    }

    test("sendCommand validates timeoutMs must be positive") {
        val client = createClient()
        val cmd = commandMessage {
            channel = "commands-ch"
            timeoutMs = 0
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendCommand(cmd)
        }
        client.close()
    }

    test("subscribeToCommands receives commands from server") {
        val client = createClient()

        service.requestsToEmit = listOf(
            kubemq.Kubemq.Request.newBuilder()
                .setRequestID("cmd-recv-1")
                .setChannel("commands-ch")
                .setMetadata("action")
                .setBody(ByteString.copyFromUtf8("command-body"))
                .setReplyChannel("reply-ch")
                .putTags("x-kubemq-client-id", "sender-1")
                .build(),
        )

        val commands = client.subscribeToCommands {
            channel = "commands-ch"
            group = "cmd-group"
        }.toList()

        commands.size shouldBe 1
        commands[0].id shouldBe "cmd-recv-1"
        commands[0].channel shouldBe "commands-ch"
        commands[0].metadata shouldBe "action"
        String(commands[0].body) shouldBe "command-body"
        commands[0].replyChannel shouldBe "reply-ch"

        service.capturedSubscriptions.size shouldBe 1
        service.capturedSubscriptions[0].channel shouldBe "commands-ch"
        service.capturedSubscriptions[0].group shouldBe "cmd-group"
        service.capturedSubscriptions[0].subscribeTypeData shouldBe kubemq.Kubemq.Subscribe.SubscribeType.Commands

        client.close()
    }

    test("sendCommandResponse sends response to server") {
        val client = createClient()

        // Simulate a received command and build response from it
        val received = CommandReceived(
            id = "cmd-1",
            channel = "commands-ch",
            metadata = "action",
            body = ByteArray(0),
            replyChannel = "reply-ch",
            tags = emptyMap(),
        )

        val responseMsg = received.respond {
            executed = true
            metadata = "done"
        }

        client.sendCommandResponse(responseMsg)

        service.capturedResponses.size shouldBe 1
        service.capturedResponses[0].requestID shouldBe "cmd-1"
        service.capturedResponses[0].replyChannel shouldBe "reply-ch"
        service.capturedResponses[0].executed shouldBe true

        client.close()
    }

    test("sendCommandResponse with blank replyChannel throws") {
        val client = createClient()

        val responseMsg = CommandReceived(
            id = "cmd-1",
            channel = "commands-ch",
            metadata = "action",
            body = ByteArray(0),
            replyChannel = "",
            tags = emptyMap(),
        ).respond {
            executed = true
        }

        shouldThrow<KubeMQException.Validation> {
            client.sendCommandResponse(responseMsg)
        }
        client.close()
    }
})
