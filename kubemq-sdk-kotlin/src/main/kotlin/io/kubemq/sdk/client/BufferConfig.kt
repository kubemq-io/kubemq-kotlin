package io.kubemq.sdk.client

import io.kubemq.sdk.common.Defaults

/**
 * Configuration for message buffering during connection outages.
 *
 * When enabled, messages sent while the client is disconnected are buffered
 * in memory and replayed in FIFO order once the connection is restored.
 *
 * @see ClientConfig.buffer
 * @see BufferOverflowPolicy
 */
@KubeMQDsl
public class BufferConfig {
    /** Whether message buffering is enabled. Default: `false`. */
    public var enabled: Boolean = false

    /** Maximum number of messages to buffer. Default: `1000`. */
    public var maxSize: Int = Defaults.BUFFER_MAX_SIZE

    /** Policy when the buffer is full. Default: [BufferOverflowPolicy.REJECT]. */
    public var overflowPolicy: BufferOverflowPolicy = BufferOverflowPolicy.REJECT
}
