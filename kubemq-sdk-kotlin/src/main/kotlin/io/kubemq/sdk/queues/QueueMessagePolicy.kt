package io.kubemq.sdk.queues

import io.kubemq.sdk.client.KubeMQDsl

/**
 * Delivery policy for a queue message, controlling expiration, delay, and dead-letter routing.
 *
 * @see QueueMessage.policy
 */
@KubeMQDsl
public data class QueueMessagePolicy(
    /** Time in seconds until the message expires and is removed. `0` means no expiration. */
    public var expirationSeconds: Int = 0,
    /** Time in seconds to delay message availability after sending. `0` means immediate. */
    public var delaySeconds: Int = 0,
    /** Maximum number of receive attempts before routing to the dead-letter queue. `0` means unlimited. */
    public var maxReceiveCount: Int = 0,
    /** Target queue channel for dead-letter routing when [maxReceiveCount] is exceeded. */
    public var maxReceiveQueue: String = "",
) {
    internal fun toProto(): kubemq.Kubemq.QueueMessagePolicy =
        kubemq.Kubemq.QueueMessagePolicy.newBuilder()
            .setExpirationSeconds(expirationSeconds)
            .setDelaySeconds(delaySeconds)
            .setMaxReceiveCount(maxReceiveCount)
            .setMaxReceiveQueue(maxReceiveQueue)
            .build()

    internal companion object {
        fun fromProto(proto: kubemq.Kubemq.QueueMessagePolicy): QueueMessagePolicy =
            QueueMessagePolicy(
                expirationSeconds = proto.expirationSeconds,
                delaySeconds = proto.delaySeconds,
                maxReceiveCount = proto.maxReceiveCount,
                maxReceiveQueue = proto.maxReceiveQueue,
            )
    }
}
