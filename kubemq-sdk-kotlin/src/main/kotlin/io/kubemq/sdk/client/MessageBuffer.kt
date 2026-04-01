package io.kubemq.sdk.client

import io.kubemq.sdk.exception.KubeMQException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList

internal class MessageBuffer(private val config: BufferConfig) {
    private val queue = LinkedList<BufferedMessage>()                               // M-5
    private val mutex = Mutex()                                                    // M-5

    suspend fun offer(message: BufferedMessage) {
        if (!config.enabled) {
            throw KubeMQException.Throttling("Buffer disabled", operation = "buffer")
        }
        mutex.withLock {                                                           // M-5
            when (config.overflowPolicy) {
                BufferOverflowPolicy.REJECT -> {
                    if (queue.size >= config.maxSize) {
                        throw KubeMQException.Throttling("Message buffer full")
                    }
                    queue.add(message)
                }
                BufferOverflowPolicy.DROP_OLDEST -> {
                    if (queue.size >= config.maxSize) {
                        queue.poll()
                    }
                    queue.add(message)
                }
                BufferOverflowPolicy.DROP_NEWEST -> {
                    if (queue.size < config.maxSize) {
                        queue.add(message)
                    }
                }
                BufferOverflowPolicy.BLOCK -> {
                    // Note: BLOCK policy with mutex means callers will wait on mutex
                    // This is acceptable for a suspend function
                    queue.add(message)
                }
            }
        }
    }

    suspend fun drain(): List<BufferedMessage> {                                    // FIX-3: suspend + mutex
        return mutex.withLock {
            val messages = mutableListOf<BufferedMessage>()
            while (queue.isNotEmpty()) {
                messages.add(queue.poll())
            }
            messages
        }
    }

    suspend fun discardAll(): Int {                                                 // FIX-3: suspend + mutex
        return mutex.withLock {
            val count = queue.size
            queue.clear()
            count
        }
    }

    suspend fun currentSize(): Int = mutex.withLock { queue.size }
}
