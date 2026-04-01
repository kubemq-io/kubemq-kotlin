package io.kubemq.sdk.pubsub

/**
 * Result of publishing an event or event store message.
 *
 * For fire-and-forget events, [sent] indicates successful transmission.
 * For events store, [sent] confirms the message was persisted by the broker.
 *
 * @see PubSubClient.publishEventStore
 */
public data class EventSendResult(
    /** Event identifier matching the published message. */
    public val id: String,
    /** `true` if the event was successfully sent/persisted. */
    public val sent: Boolean,
    /** Error message if [sent] is `false`; empty string otherwise. */
    public val error: String = "",
) {
    internal companion object {
        fun decode(result: kubemq.Kubemq.Result): EventSendResult = EventSendResult(
            id = result.eventID,
            sent = result.sent,
            error = result.error ?: "",
        )
    }
}
