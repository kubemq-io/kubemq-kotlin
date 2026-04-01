package io.kubemq.sdk.client

import io.kubemq.sdk.common.Defaults
import io.kubemq.sdk.transport.TlsConfig

/**
 * Configuration for a KubeMQ client connection.
 *
 * All properties have sensible defaults and can also be populated from environment
 * variables via [fromEnvironment]. The [address] and [clientId] fall back to
 * `KUBEMQ_ADDRESS` and `KUBEMQ_CLIENT_ID` environment variables respectively.
 *
 * Example:
 * ```kotlin
 * val client = KubeMQClient.pubSub {
 *     address = "localhost:50000"
 *     clientId = "my-service"
 *     logLevel = LogLevel.DEBUG
 *     tls {
 *         certFile = "/path/to/cert.pem"
 *         keyFile = "/path/to/key.pem"
 *     }
 *     reconnection {
 *         maxRetries = 10
 *         initialBackoffMs = 1000
 *     }
 * }
 * ```
 *
 * @see TlsConfig
 * @see ReconnectionConfig
 * @see BufferConfig
 */
@KubeMQDsl
public class ClientConfig {
    /** Broker gRPC address (host:port). Defaults to `KUBEMQ_ADDRESS` env var or `"localhost:50000"`. */
    public var address: String = ""

    /** Unique client identifier. Defaults to `KUBEMQ_CLIENT_ID` env var or an auto-generated UUID. */
    public var clientId: String = ""

    /** Bearer token for authentication. Defaults to `KUBEMQ_AUTH_TOKEN` env var or empty string. */
    public var authToken: String = ""

    /** TLS configuration. Set to `null` (default) for plaintext connections. */
    public var tls: TlsConfig? = null

    /** Maximum inbound gRPC message size in bytes. Default: `104857600` (100 MB). */
    public var maxReceiveSize: Int = Defaults.MAX_RECEIVE_SIZE

    /** Whether gRPC keep-alive is enabled. Default: `true`. */
    public var keepAlive: Boolean = true

    /** Interval in seconds between gRPC keep-alive pings. Default: `10`. */
    public var pingIntervalSeconds: Int = Defaults.PING_INTERVAL_SECONDS

    /** Timeout in seconds for gRPC keep-alive pings. Default: `5`. */
    public var pingTimeoutSeconds: Int = Defaults.PING_TIMEOUT_SECONDS

    /** Logging verbosity level. Default: [LogLevel.INFO]. */
    public var logLevel: LogLevel = LogLevel.INFO

    /** Timeout in milliseconds to wait for connection readiness in [ensureConnected]. Default: `30000`. */
    public var ensureConnectedTimeoutMs: Long = 30_000L

    /** Timeout in milliseconds for unary gRPC calls. Default: `60000`. */
    public var unaryTimeoutMs: Long = 60_000L

    internal var reconnectionConfig: ReconnectionConfig = ReconnectionConfig()
    internal var bufferConfig: BufferConfig = BufferConfig()

    /**
     * Configures TLS settings using a DSL block.
     *
     * @param block Configuration block for [TlsConfig]
     */
    public fun tls(block: TlsConfig.() -> Unit) {
        tls = TlsConfig().apply(block)
    }

    /**
     * Configures reconnection behavior using a DSL block.
     *
     * @param block Configuration block for [ReconnectionConfig]
     */
    public fun reconnection(block: ReconnectionConfig.() -> Unit) {
        reconnectionConfig = ReconnectionConfig().apply(block)
    }

    /**
     * Configures message buffering during disconnections using a DSL block.
     *
     * @param block Configuration block for [BufferConfig]
     */
    public fun buffer(block: BufferConfig.() -> Unit) {
        bufferConfig = BufferConfig().apply(block)
    }

    public companion object {
        /**
         * Creates a [ClientConfig] populated from environment variables.
         *
         * Reads: `KUBEMQ_ADDRESS`, `KUBEMQ_CLIENT_ID`, `KUBEMQ_AUTH_TOKEN`,
         * `KUBEMQ_TLS_CERT_FILE`, `KUBEMQ_TLS_KEY_FILE`, `KUBEMQ_TLS_CA_FILE`,
         * `KUBEMQ_LOG_LEVEL`.
         *
         * @return A [ClientConfig] with values from the environment (unset vars use defaults)
         */
        public fun fromEnvironment(): ClientConfig {
            val config = ClientConfig()
            System.getenv("KUBEMQ_ADDRESS")?.takeIf { it.isNotBlank() }?.let {
                config.address = it
            }
            System.getenv("KUBEMQ_CLIENT_ID")?.takeIf { it.isNotBlank() }?.let {
                config.clientId = it
            }
            System.getenv("KUBEMQ_AUTH_TOKEN")?.takeIf { it.isNotBlank() }?.let {
                config.authToken = it
            }
            System.getenv("KUBEMQ_TLS_CERT_FILE")?.takeIf { it.isNotBlank() }?.let { certFile ->
                System.getenv("KUBEMQ_TLS_KEY_FILE")?.takeIf { it.isNotBlank() }?.let { keyFile ->
                    config.tls = TlsConfig().apply {
                        this.certFile = certFile
                        this.keyFile = keyFile
                        System.getenv("KUBEMQ_TLS_CA_FILE")?.takeIf { it.isNotBlank() }?.let {
                            this.caCertFile = it
                        }
                    }
                }
            }
            System.getenv("KUBEMQ_LOG_LEVEL")?.takeIf { it.isNotBlank() }?.let { level ->
                try {
                    config.logLevel = LogLevel.valueOf(level.uppercase())
                } catch (_: IllegalArgumentException) {
                    // ignore invalid log level from env
                }
            }
            return config
        }
    }
}
