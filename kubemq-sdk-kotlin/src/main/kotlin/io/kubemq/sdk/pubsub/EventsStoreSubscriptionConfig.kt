package io.kubemq.sdk.pubsub

import io.kubemq.sdk.client.KubeMQDsl

/**
 * Configuration for subscribing to a KubeMQ events store channel.
 *
 * @see PubSubClient.subscribeToEventsStore
 * @see StartPosition
 */
@KubeMQDsl
public class EventsStoreSubscriptionConfig {
    /** Channel name to subscribe to. Wildcards are NOT supported. Required. */
    public var channel: String = ""

    /** Consumer group name for load-balanced delivery. Leave empty for broadcast delivery. */
    public var group: String = ""

    /** Starting position for message replay. Default: [StartPosition.StartNewOnly]. */
    public var startPosition: StartPosition = StartPosition.StartNewOnly
}
