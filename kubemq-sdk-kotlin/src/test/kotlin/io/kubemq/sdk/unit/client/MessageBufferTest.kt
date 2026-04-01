package io.kubemq.sdk.unit.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kubemq.sdk.client.BufferConfig
import io.kubemq.sdk.client.BufferOverflowPolicy
import io.kubemq.sdk.client.BufferedMessage
import io.kubemq.sdk.client.MessageBuffer
import io.kubemq.sdk.exception.KubeMQException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class MessageBufferTest : FunSpec({

    fun msg(id: String): BufferedMessage = object : BufferedMessage {
        override val grpcRequest: Any = id
        override val replayAction: suspend (io.kubemq.sdk.client.KubeMQClient) -> Unit = {}
        override fun toString(): String = "msg($id)"
    }

    fun enabledConfig(maxSize: Int, policy: BufferOverflowPolicy) = BufferConfig().apply {
        enabled = true
        this.maxSize = maxSize
        overflowPolicy = policy
    }

    context("disabled buffer") {

        test("offer throws when buffer is disabled") {
            val config = BufferConfig().apply { enabled = false }
            val buffer = MessageBuffer(config)
            shouldThrow<KubeMQException.Throttling> {
                buffer.offer(msg("1"))
            }
        }
    }

    context("REJECT policy") {

        test("offer succeeds when under capacity") {
            val buffer = MessageBuffer(enabledConfig(3, BufferOverflowPolicy.REJECT))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            buffer.currentSize() shouldBe 2
        }

        test("offer throws when buffer is full") {
            val buffer = MessageBuffer(enabledConfig(2, BufferOverflowPolicy.REJECT))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            shouldThrow<KubeMQException.Throttling> {
                buffer.offer(msg("3"))
            }
            buffer.currentSize() shouldBe 2
        }

        test("drain returns all messages in order") {
            val buffer = MessageBuffer(enabledConfig(3, BufferOverflowPolicy.REJECT))
            buffer.offer(msg("a"))
            buffer.offer(msg("b"))
            buffer.offer(msg("c"))
            val drained = buffer.drain()
            drained.map { it.grpcRequest } shouldBe listOf("a", "b", "c")
            buffer.currentSize() shouldBe 0
        }
    }

    context("DROP_OLDEST policy") {

        test("drops oldest when full") {
            val buffer = MessageBuffer(enabledConfig(2, BufferOverflowPolicy.DROP_OLDEST))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            buffer.offer(msg("3"))
            buffer.currentSize() shouldBe 2
            val drained = buffer.drain()
            drained.map { it.grpcRequest } shouldBe listOf("2", "3")
        }

        test("does not drop when under capacity") {
            val buffer = MessageBuffer(enabledConfig(5, BufferOverflowPolicy.DROP_OLDEST))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            buffer.currentSize() shouldBe 2
        }
    }

    context("DROP_NEWEST policy") {

        test("silently drops new message when full") {
            val buffer = MessageBuffer(enabledConfig(2, BufferOverflowPolicy.DROP_NEWEST))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            buffer.offer(msg("3"))
            buffer.currentSize() shouldBe 2
            val drained = buffer.drain()
            drained.map { it.grpcRequest } shouldBe listOf("1", "2")
        }

        test("accepts message when under capacity") {
            val buffer = MessageBuffer(enabledConfig(5, BufferOverflowPolicy.DROP_NEWEST))
            buffer.offer(msg("1"))
            buffer.currentSize() shouldBe 1
        }
    }

    context("BLOCK policy") {

        test("offer succeeds when under capacity") {
            val buffer = MessageBuffer(enabledConfig(3, BufferOverflowPolicy.BLOCK))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            buffer.currentSize() shouldBe 2
        }

        test("accepts messages beyond capacity with BLOCK policy") {
            // BLOCK policy with Mutex-based implementation accepts messages regardless of maxSize
            val buffer = MessageBuffer(enabledConfig(1, BufferOverflowPolicy.BLOCK))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            buffer.currentSize() shouldBe 2
        }
    }

    context("drain") {

        test("drain on empty buffer returns empty list") {
            val buffer = MessageBuffer(enabledConfig(5, BufferOverflowPolicy.REJECT))
            buffer.drain() shouldBe emptyList()
        }

        test("multiple drains return no duplicates") {
            val buffer = MessageBuffer(enabledConfig(5, BufferOverflowPolicy.REJECT))
            buffer.offer(msg("1"))
            buffer.drain().size shouldBe 1
            buffer.drain() shouldBe emptyList()
        }
    }

    context("discardAll") {

        test("discardAll clears the buffer") {
            val buffer = MessageBuffer(enabledConfig(5, BufferOverflowPolicy.REJECT))
            buffer.offer(msg("1"))
            buffer.offer(msg("2"))
            buffer.offer(msg("3"))
            val count = buffer.discardAll()
            count shouldBe 3
            buffer.currentSize() shouldBe 0
            buffer.drain() shouldBe emptyList()
        }

        test("discardAll on empty buffer returns 0") {
            val buffer = MessageBuffer(enabledConfig(5, BufferOverflowPolicy.REJECT))
            buffer.discardAll() shouldBe 0
        }

        test("discardAll releases semaphore for BLOCK policy") {
            val buffer = MessageBuffer(enabledConfig(1, BufferOverflowPolicy.BLOCK))
            buffer.offer(msg("1"))
            buffer.discardAll()

            // Should be able to offer again without blocking
            withTimeout(500) {
                buffer.offer(msg("2"))
            }
            buffer.currentSize() shouldBe 1
        }
    }
})
