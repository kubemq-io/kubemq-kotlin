package io.kubemq.sdk.common

/**
 * Information about the KubeMQ broker, returned by [io.kubemq.sdk.client.KubeMQClient.ping].
 *
 * @see io.kubemq.sdk.client.KubeMQClient.ping
 */
public data class ServerInfo(
    /** Hostname of the KubeMQ broker. */
    public val host: String,
    /** Version string of the KubeMQ broker (e.g., "2.5.0"). */
    public val version: String,
    /** Epoch timestamp (seconds) when the server started. */
    public val serverStartTime: Long,
    /** Number of seconds the server has been running. */
    public val serverUpTimeSeconds: Long,
)
