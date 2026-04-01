package io.kubemq.sdk.common

/**
 * Information about a KubeMQ channel, returned by list-channel operations.
 *
 * @see io.kubemq.sdk.pubsub.PubSubClient.listEventsChannels
 * @see io.kubemq.sdk.cq.CQClient.listCommandsChannels
 * @see io.kubemq.sdk.queues.QueuesClient.listQueuesChannels
 */
public data class ChannelInfo(
    /** Channel name. */
    public val name: String,
    /** Channel type string (e.g., "events", "events_store", "queues", "commands", "queries"). */
    public val type: String,
    /** Epoch timestamp (nanoseconds) of last activity on this channel. */
    public val lastActivity: Long,
    /** `true` if the channel currently has active subscribers. */
    public val isActive: Boolean,
    /** Statistics for incoming messages. */
    public val incoming: ChannelStats,
    /** Statistics for outgoing messages. */
    public val outgoing: ChannelStats,
)

/**
 * Message statistics for a channel direction (incoming or outgoing).
 *
 * @see ChannelInfo.incoming
 * @see ChannelInfo.outgoing
 */
public data class ChannelStats(
    /** Total number of messages. */
    public val messages: Long,
    /** Total volume in bytes. */
    public val volume: Long,
    /** Number of messages currently waiting (queues only). */
    public val waiting: Long,
    /** Number of messages that expired. */
    public val expired: Long,
    /** Number of messages currently delayed. */
    public val delayed: Long,
)
