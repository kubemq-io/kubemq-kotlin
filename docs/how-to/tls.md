# How To: Configure TLS and mTLS

This guide shows how to secure KubeMQ connections with TLS (server authentication) and mTLS (mutual authentication).

## Prerequisites

- KubeMQ broker configured with TLS enabled
- TLS certificate files (PEM format)
- KubeMQ Kotlin SDK installed

## TLS (Server Authentication)

TLS encrypts the connection and verifies the server's identity using a CA certificate.

### File-Based Configuration

```kotlin
import io.kubemq.sdk.client.KubeMQClient

val client = KubeMQClient.pubSub {
    address = "broker.example.com:50000"
    tls {
        caCertFile = "/path/to/ca.pem"
    }
}

val info = client.ping()
println("Connected securely to ${info.host}")
client.close()
```

### PEM-Based Configuration

```kotlin
val caCert = File("/path/to/ca.pem").readBytes()

val client = KubeMQClient.pubSub {
    address = "broker.example.com:50000"
    tls {
        caCertPem = caCert
    }
}
```

### Environment Variable Configuration

```bash
export KUBEMQ_TLS_CA_FILE=/path/to/ca.pem
```

```kotlin
val client = KubeMQClient.pubSub {
    // TLS is auto-configured from environment variables
}
```

## mTLS (Mutual Authentication)

mTLS adds client certificate authentication on top of TLS. The server verifies the client's identity.

### File-Based Configuration

```kotlin
val client = KubeMQClient.pubSub {
    address = "broker.example.com:50000"
    tls {
        certFile = "/path/to/client-cert.pem"
        keyFile = "/path/to/client-key.pem"
        caCertFile = "/path/to/ca.pem"
    }
}

val info = client.ping()
println("Connected with mTLS to ${info.host}")
client.close()
```

### PEM-Based Configuration

```kotlin
val cert = File("/path/to/client-cert.pem").readBytes()
val key = File("/path/to/client-key.pem").readBytes()
val ca = File("/path/to/ca.pem").readBytes()

val client = KubeMQClient.pubSub {
    address = "broker.example.com:50000"
    tls {
        certPem = cert
        keyPem = key
        caCertPem = ca
    }
}
```

### Environment Variable Configuration

```bash
export KUBEMQ_TLS_CERT_FILE=/path/to/client-cert.pem
export KUBEMQ_TLS_KEY_FILE=/path/to/client-key.pem
export KUBEMQ_TLS_CA_FILE=/path/to/ca.pem
```

## TlsConfig Reference

| Property | Type | Description |
|----------|------|-------------|
| `certFile` | `String` | Path to client certificate PEM file |
| `keyFile` | `String` | Path to client private key PEM file |
| `caCertFile` | `String` | Path to CA certificate PEM file |
| `certPem` | `ByteArray` | Client certificate as PEM bytes |
| `keyPem` | `ByteArray` | Client private key as PEM bytes |
| `caCertPem` | `ByteArray` | CA certificate as PEM bytes |
| `insecureSkipVerify` | `Boolean` | Skip server cert verification (dev only) |

## Development Mode

For local development without valid certificates:

```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    tls {
        insecureSkipVerify = true // DO NOT USE IN PRODUCTION
    }
}
```

> **Warning:** `insecureSkipVerify = true` disables server certificate verification.
> Use only in development environments.

## Using TLS with All Client Types

TLS configuration works identically across all client types:

```kotlin
// PubSub with TLS
val pubsub = KubeMQClient.pubSub {
    address = "broker.example.com:50000"
    tls {
        certFile = "/path/to/client-cert.pem"
        keyFile = "/path/to/client-key.pem"
        caCertFile = "/path/to/ca.pem"
    }
}

// Queues with TLS
val queues = KubeMQClient.queues {
    address = "broker.example.com:50000"
    tls {
        certFile = "/path/to/client-cert.pem"
        keyFile = "/path/to/client-key.pem"
        caCertFile = "/path/to/ca.pem"
    }
}

// CQ with TLS
val cq = KubeMQClient.cq {
    address = "broker.example.com:50000"
    tls {
        certFile = "/path/to/client-cert.pem"
        keyFile = "/path/to/client-key.pem"
        caCertFile = "/path/to/ca.pem"
    }
}
```

## Generating Test Certificates

```bash
# Generate CA key and certificate
openssl req -x509 -newkey rsa:4096 -keyout ca-key.pem -out ca.pem -days 365 -nodes \
    -subj "/CN=KubeMQ CA"

# Generate server key and CSR
openssl req -newkey rsa:4096 -keyout server-key.pem -out server.csr -nodes \
    -subj "/CN=localhost"

# Sign server certificate
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
    -out server-cert.pem -days 365

# Generate client key and CSR
openssl req -newkey rsa:4096 -keyout client-key.pem -out client.csr -nodes \
    -subj "/CN=my-client"

# Sign client certificate
openssl x509 -req -in client.csr -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
    -out client-cert.pem -days 365
```

## Kubernetes TLS Setup

When running in Kubernetes, mount certificates as secrets:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: kubemq-client-tls
type: kubernetes.io/tls
data:
  tls.crt: <base64-encoded-cert>
  tls.key: <base64-encoded-key>
  ca.crt: <base64-encoded-ca>
```

Then reference the mounted paths in your client configuration:

```kotlin
val client = KubeMQClient.pubSub {
    address = "kubemq-cluster:50000"
    tls {
        certFile = "/etc/tls/tls.crt"
        keyFile = "/etc/tls/tls.key"
        caCertFile = "/etc/tls/ca.crt"
    }
}
```

## Troubleshooting

| Error | Cause | Solution |
|-------|-------|----------|
| `UNAVAILABLE: Network closed` | Server TLS enabled, client not | Add TLS config |
| `UNAVAILABLE: TLS handshake` | Certificate mismatch | Verify CA cert matches server |
| `SSL_ERROR_HANDSHAKE` | Expired certificate | Regenerate certificates |
| `UNAUTHENTICATED` | Missing client cert for mTLS | Add `certFile`/`keyFile` |

## Related

- [API Reference: TlsConfig](https://kubemq.github.io/kubemq-kotlin/io.kubemq.sdk.transport/-tls-config/)
- [Example: TLS Setup](../examples/src/main/kotlin/io/kubemq/sdk/examples/tls/TlsSetupExample.kt)
- [Example: mTLS Setup](../examples/src/main/kotlin/io/kubemq/sdk/examples/tls/MtlsSetupExample.kt)
