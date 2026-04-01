package io.kubemq.sdk.examples.tls

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50001"
private const val CLIENT_ID = "kotlin-tls-mtls-setup"
private const val CA_CERT_FILE = "/path/to/ca.pem"
private const val CLIENT_CERT_FILE = "/path/to/client.pem"
private const val CLIENT_KEY_FILE = "/path/to/client.key"

fun main() = runBlocking {
    connectWithMutualTls()
    connectWithMutualTlsFromPemBytes()
    println("mTLS setup examples completed.")
}

private fun connectWithMutualTls() {
    println("=== Mutual TLS (mTLS) Connection ===\n")

    // Create a client with mTLS (client cert + key + CA cert)
    try {
        val client = KubeMQClient.queues {
            address = ADDRESS
            clientId = CLIENT_ID
            tls {
                caCertFile = CA_CERT_FILE
                certFile = CLIENT_CERT_FILE
                keyFile = CLIENT_KEY_FILE
            }
        }
        println("mTLS client configured with file paths:")
        println("  CA: $CA_CERT_FILE")
        println("  Cert: $CLIENT_CERT_FILE")
        println("  Key: $CLIENT_KEY_FILE")
        // Uncomment when mTLS server is available:
        // client.use { println("Connected: ${it.ping().host}") }
        client.close()
    } catch (e: Exception) {
        println("mTLS connection failed: ${e.message}")
    }
}

private fun connectWithMutualTlsFromPemBytes() {
    println("\n=== Mutual TLS from PEM bytes ===\n")

    // Load certs from PEM bytes instead of files
    val caBytes = "-----BEGIN CERTIFICATE-----\n... CA cert ...\n-----END CERTIFICATE-----".toByteArray()
    val certBytes = "-----BEGIN CERTIFICATE-----\n... client cert ...\n-----END CERTIFICATE-----".toByteArray()
    val keyBytes = "-----BEGIN PRIVATE KEY-----\n... client key ...\n-----END PRIVATE KEY-----".toByteArray()

    try {
        val client = KubeMQClient.queues {
            address = ADDRESS
            clientId = "$CLIENT_ID-pem"
            tls {
                caCertPem = caBytes
                certPem = certBytes
                keyPem = keyBytes
            }
        }
        println("mTLS client configured with PEM bytes.")
        // Uncomment when mTLS server is available:
        // client.use { println("Connected: ${it.ping().host}") }
        client.close()
    } catch (e: Exception) {
        println("mTLS PEM connection failed: ${e.message}")
    }
}
