package io.kubemq.sdk.examples.tls

import io.kubemq.sdk.client.KubeMQClient
import kotlinx.coroutines.runBlocking

private const val ADDRESS = "localhost:50001"
private const val CLIENT_ID = "kotlin-tls-tls-setup"
private const val CA_CERT_FILE = "/path/to/ca.pem"

fun main() = runBlocking {
    connectWithTlsValidation()
    // Uncomment when TLS server is available:
    // connectWithServerTls()
    println("TLS setup examples completed.")
}

private suspend fun connectWithServerTls() {
    println("=== Server-Side TLS Connection ===\n")

    // Create a client with server-side TLS (CA cert for verification)
    try {
        val client = KubeMQClient.queues {
            address = ADDRESS
            clientId = CLIENT_ID
            tls {
                caCertFile = CA_CERT_FILE
            }
        }
        client.use {
            val info = it.ping()
            println("Successfully connected with server-side TLS!")
            println("Server Info: host=${info.host}, version=${info.version}")
        }
    } catch (e: Exception) {
        println("TLS connection failed: ${e.message}")
    }
}

private fun connectWithTlsValidation() {
    println("=== TLS Validation ===\n")

    // Demonstrate TLS configuration (does not require live TLS server)
    println("Test: TLS configuration with cert and key...")
    try {
        val client = KubeMQClient.queues {
            address = ADDRESS
            clientId = CLIENT_ID
            tls {
                certFile = "/path/to/client.pem"
                keyFile = "/path/to/client-key.pem"
                caCertFile = CA_CERT_FILE
            }
        }
        println("TLS client configured successfully.")
        println("  certFile, keyFile, and caCertFile all set.")
        client.close()
    } catch (e: Exception) {
        println("Configuration error: ${e.message}")
    }

    println()
}
