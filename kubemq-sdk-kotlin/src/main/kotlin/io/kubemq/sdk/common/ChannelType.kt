package io.kubemq.sdk.common

/**
 * Enumeration of KubeMQ channel types.
 *
 * @property value The string identifier used in channel management gRPC calls.
 */
public enum class ChannelType(public val value: String) {
    /** Fire-and-forget event channels. */
    EVENTS("events"),

    /** Persistent event channels with message replay support. */
    EVENTS_STORE("events_store"),

    /** Queue channels with guaranteed delivery. */
    QUEUES("queues"),

    /** Command channels for fire-and-execute request-response. */
    COMMANDS("commands"),

    /** Query channels for request-response with data. */
    QUERIES("queries"),
}
