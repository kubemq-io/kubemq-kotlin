package io.kubemq.sdk.queues

import io.kubemq.sdk.client.KubeMQDsl
import io.kubemq.sdk.common.Validation
import io.kubemq.sdk.exception.KubeMQException
import java.util.UUID

/**
 * Configuration for receiving messages from a queue using the simple (unary) API.
 *
 * Messages received via the simple API are auto-acknowledged.
 *
 * @see QueuesClient.receiveQueueMessages
 * @see QueuesClient.peekQueueMessages
 */
@KubeMQDsl
public class SimpleQueueReceiveConfig {
    /** Queue channel to receive from. Required. */
    public var channel: String = ""

    /** Maximum number of messages to receive. Must be > 0. Default: `1`. */
    public var maxNumberOfMessages: Int = 1

    /** Timeout in seconds to wait for messages. Must be > 0. Default: `1`. */
    public var waitTimeSeconds: Int = 1

    internal fun validate() {
        Validation.validateChannel(channel, "SimpleQueueReceiveConfig.validate")
        if (maxNumberOfMessages < 1) {
            throw KubeMQException.Validation(
                "maxNumberOfMessages must be > 0",
                operation = "SimpleQueueReceiveConfig.validate",
                channel = channel,
            )
        }
        if (waitTimeSeconds < 1) {
            throw KubeMQException.Validation(
                "waitTimeSeconds must be > 0",
                operation = "SimpleQueueReceiveConfig.validate",
                channel = channel,
            )
        }
    }

    internal fun toProto(clientId: String, isPeek: Boolean = false): kubemq.Kubemq.ReceiveQueueMessagesRequest =
        kubemq.Kubemq.ReceiveQueueMessagesRequest.newBuilder()
            .setRequestID(UUID.randomUUID().toString())
            .setClientID(clientId)
            .setChannel(channel)
            .setMaxNumberOfMessages(maxNumberOfMessages)
            .setWaitTimeSeconds(waitTimeSeconds)
            .setIsPeak(isPeek)
            .build()
}

/**
 * Creates a [SimpleQueueReceiveConfig] using a DSL builder.
 *
 * @param init Configuration block for [SimpleQueueReceiveConfig]
 * @return Configured [SimpleQueueReceiveConfig] instance
 */
public fun simpleQueueReceiveConfig(init: SimpleQueueReceiveConfig.() -> Unit): SimpleQueueReceiveConfig =
    SimpleQueueReceiveConfig().apply(init)
