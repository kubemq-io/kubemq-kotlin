package io.kubemq.sdk.queues

import io.kubemq.sdk.client.KubeMQDsl
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.KubeMQException
import java.util.UUID

/**
 * Configuration for receiving messages from a queue using the stream API.
 *
 * Example:
 * ```kotlin
 * val response = queues.receiveQueuesMessages {
 *     channel = "queues.tasks"
 *     maxItems = 10
 *     waitTimeoutMs = 5000
 *     autoAck = true
 * }
 * ```
 *
 * @see QueuesClient.receiveQueuesMessages
 */
@KubeMQDsl
public class QueueReceiveConfig {
    /** Queue channel to receive from. Required. */
    public var channel: String = ""

    /** Maximum number of messages to receive in one poll. Must be > 0. Default: `1`. */
    public var maxItems: Int = 1

    /** Timeout in milliseconds to wait for messages. Must be > 0. Default: `5000`. */
    public var waitTimeoutMs: Int = 5000

    /** If `true`, messages are automatically acknowledged on receipt. Default: `false`. */
    public var autoAck: Boolean = false
    // visibilitySeconds REMOVED -- proto QueuesDownstreamRequest has no such field (M-4)

    internal fun validate() {
        Validation.validateChannel(channel, "QueueReceiveConfig.validate")
        if (maxItems < 1) {
            throw KubeMQException.Validation(
                "maxItems must be > 0",
                operation = "QueueReceiveConfig.validate",
                channel = channel,
            )
        }
        if (waitTimeoutMs < 1) {
            throw KubeMQException.Validation(
                "waitTimeoutMs must be > 0",
                operation = "QueueReceiveConfig.validate",
                channel = channel,
            )
        }
        // Removed visibilitySeconds validation and autoAck mutual exclusion (M-4)
    }

    internal fun toProto(clientId: String): kubemq.Kubemq.QueuesDownstreamRequest =
        kubemq.Kubemq.QueuesDownstreamRequest.newBuilder()
            .setRequestID(UUID.randomUUID().toString())
            .setClientID(clientId)
            .setChannel(channel)
            .setMaxItems(maxItems)
            .setWaitTimeout(waitTimeoutMs)
            .setAutoAck(autoAck)
            .setRequestTypeData(kubemq.Kubemq.QueuesDownstreamRequestType.Get)
            .build()
}

/**
 * Creates a [QueueReceiveConfig] using a DSL builder.
 *
 * @param init Configuration block for [QueueReceiveConfig]
 * @return Configured [QueueReceiveConfig] instance
 */
public fun queueReceiveConfig(init: QueueReceiveConfig.() -> Unit): QueueReceiveConfig =
    QueueReceiveConfig().apply(init)
