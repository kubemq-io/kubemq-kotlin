package io.kubemq.sdk.pubsub

import io.kubemq.sdk.client.KubeMQDsl

/**
 * Configuration for subscribing to a KubeMQ events channel.
 *
 * @see PubSubClient.subscribeToEvents
 */
@KubeMQDsl
public class EventsSubscriptionConfig {
    /** Channel name to subscribe to. Wildcards are supported (e.g., `"events.*"`). Required. */
    public var channel: String = ""

    /** Consumer group name for load-balanced delivery. Leave empty for broadcast delivery. */
    public var group: String = ""
}
