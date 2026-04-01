package io.kubemq.sdk.pubsub

/**
 * An event message received from a subscription.
 *
 * Contains the original message data plus server-assigned metadata such as
 * [timestamp] and [sequence] (sequence is non-zero only for events store messages).
 *
 * @see PubSubClient.subscribeToEvents
 * @see PubSubClient.subscribeToEventsStore
 */
public data class EventMessageReceived(
    /** Server-assigned event identifier. */
    public val id: String,
    /** Channel the event was published to. */
    public val channel: String,
    /** Metadata string from the publisher. */
    public val metadata: String,
    /** Message payload as a byte array. */
    public val body: ByteArray,
    /** Client ID of the publisher. */
    public val fromClientId: String,
    /** Server timestamp in nanoseconds since epoch. */
    public val timestamp: Long,
    /** Sequence number (non-zero for events store messages, zero for events). */
    public val sequence: Long,
    /** Key-value tags from the publisher. */
    public val tags: Map<String, String>,
) {
    internal companion object {
        fun decode(event: kubemq.Kubemq.EventReceive): EventMessageReceived = EventMessageReceived(
            id = event.eventID,
            channel = event.channel,
            metadata = event.metadata,
            body = event.body.toByteArray(),
            fromClientId = event.tagsMap["x-kubemq-client-id"] ?: "",
            timestamp = event.timestamp,
            sequence = event.sequence,
            tags = event.tagsMap,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventMessageReceived) return false
        return id == other.id && channel == other.channel && metadata == other.metadata &&
            body.contentEquals(other.body) && fromClientId == other.fromClientId &&
            timestamp == other.timestamp && sequence == other.sequence && tags == other.tags
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + channel.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}
