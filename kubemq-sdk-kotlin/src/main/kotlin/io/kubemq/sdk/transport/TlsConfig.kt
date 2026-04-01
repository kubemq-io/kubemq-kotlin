package io.kubemq.sdk.transport

import io.kubemq.sdk.client.KubeMQDsl

/**
 * TLS/mTLS configuration for secure connections to the KubeMQ broker.
 *
 * Provide certificates either as file paths ([certFile], [keyFile], [caCertFile])
 * or as PEM-encoded byte arrays ([certPem], [keyPem], [caCertPem]).
 *
 * Example (file-based):
 * ```kotlin
 * val client = KubeMQClient.pubSub {
 *     address = "broker.example.com:50000"
 *     tls {
 *         certFile = "/certs/client.pem"
 *         keyFile = "/certs/client-key.pem"
 *         caCertFile = "/certs/ca.pem"
 *     }
 * }
 * ```
 *
 * @see io.kubemq.sdk.client.ClientConfig.tls
 */
@KubeMQDsl
public class TlsConfig {
    /** Path to the client certificate PEM file. */
    public var certFile: String = ""

    /** Path to the client private key PEM file. */
    public var keyFile: String = ""

    /** Path to the CA certificate PEM file for server verification. */
    public var caCertFile: String = ""

    /** Client certificate as PEM-encoded bytes (alternative to [certFile]). */
    public var certPem: ByteArray = ByteArray(0)

    /** Client private key as PEM-encoded bytes (alternative to [keyFile]). */
    public var keyPem: ByteArray = ByteArray(0)

    /** CA certificate as PEM-encoded bytes (alternative to [caCertFile]). */
    public var caCertPem: ByteArray = ByteArray(0)

    /** If `true`, skip server certificate verification. Use only in development. */
    public var insecureSkipVerify: Boolean = false

    internal val isEnabled: Boolean
        get() = certFile.isNotBlank() || keyFile.isNotBlank() || caCertFile.isNotBlank() ||
            certPem.isNotEmpty() || keyPem.isNotEmpty() || caCertPem.isNotEmpty() ||
            insecureSkipVerify
}
