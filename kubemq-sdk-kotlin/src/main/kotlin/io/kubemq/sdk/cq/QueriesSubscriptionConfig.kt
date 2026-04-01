package io.kubemq.sdk.cq

import io.kubemq.sdk.client.KubeMQDsl

/**
 * Configuration for subscribing to incoming queries on a channel.
 *
 * @see CQClient.subscribeToQueries
 */
@KubeMQDsl
public class QueriesSubscriptionConfig {
    /** Channel name to subscribe to. Required. */
    public var channel: String = ""

    /** Consumer group name for load-balanced delivery. Leave empty for broadcast delivery. */
    public var group: String = ""
}
