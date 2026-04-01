package io.kubemq.sdk.unit.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kubemq.sdk.client.ClientConfig
import io.kubemq.sdk.client.ConnectionState
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.common.ServerInfo
import io.kubemq.sdk.exception.KubeMQException
import io.kubemq.sdk.testutil.FakeKubeMQService
import io.kubemq.sdk.testutil.MockGrpcServer
import io.kubemq.sdk.transport.GrpcTransport
import io.kubemq.sdk.transport.Transport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class KubeMQClientTest : FunSpec({

    test("client initializes with default address when not configured") {
        val client = KubeMQClient.pubSub {
            // no address set, env var fallback or default
        }
        // The resolvedAddress should use default or env var
        client.resolvedAddress shouldNotBe ""
        client.close()
    }

    test("client initializes with configured address") {
        val client = KubeMQClient.pubSub {
            address = "custom-server:50000"
            clientId = "test-client"
        }
        client.resolvedAddress shouldBe "custom-server:50000"
        client.resolvedClientId shouldBe "test-client"
        client.close()
    }

    test("client generates clientId when not set") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }
        client.resolvedClientId shouldContain "kubemq-client-"
        client.close()
    }

    test("initial connection state is Idle") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }
        client.connectionState.value shouldBe ConnectionState.Idle
        client.close()
    }

    test("close is idempotent - calling close twice does not throw") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }
        client.close()
        client.close() // second close should be safe
    }

    test("close transitions to Closed state when connected") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }
        val mockTransport = mockk<Transport>()
        every { mockTransport.close() } returns Unit

        // Must be in a connected state for close to transition to Closed
        client.transport = mockTransport
        client.stateMachine.transitionTo(ConnectionState.Connecting)
        client.stateMachine.transitionTo(ConnectionState.Ready)

        client.close()
        client.connectionState.value shouldBe ConnectionState.Closed
    }

    test("close from Idle transitions to Closed") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }
        client.connectionState.value shouldBe ConnectionState.Idle
        client.close()
        client.connectionState.value shouldBe ConnectionState.Closed
    }

    test("ping with mock transport returns server info") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }

        val mockTransport = mockk<Transport>()
        val expectedInfo = ServerInfo("test-host", "2.5.0", 1000000L, 3600L)
        coEvery { mockTransport.ping() } returns expectedInfo
        every { mockTransport.close() } returns Unit

        // Inject mock transport and set state to Ready
        client.transport = mockTransport
        client.stateMachine.transitionTo(ConnectionState.Connecting)
        client.stateMachine.transitionTo(ConnectionState.Ready)

        val result = client.ping()
        result shouldBe expectedInfo

        client.close()
    }

    test("ping on closed client throws ClientClosed") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }
        client.close()

        shouldThrow<KubeMQException.ClientClosed> {
            client.ping()
        }
    }

    test("factory methods create correct client types") {
        val pubSub = KubeMQClient.pubSub { address = "localhost:50000" }
        pubSub.shouldBeInstanceOf<io.kubemq.sdk.pubsub.PubSubClient>()
        pubSub.close()

        val queues = KubeMQClient.queues { address = "localhost:50000" }
        queues.shouldBeInstanceOf<io.kubemq.sdk.queues.QueuesClient>()
        queues.close()

        val cq = KubeMQClient.cq { address = "localhost:50000" }
        cq.shouldBeInstanceOf<io.kubemq.sdk.cq.CQClient>()
        cq.close()
    }

    test("requireTransport throws when transport is null") {
        val client = KubeMQClient.pubSub {
            address = "localhost:50000"
        }
        shouldThrow<KubeMQException.ClientClosed> {
            client.requireTransport()
        }
        client.close()
    }
})
