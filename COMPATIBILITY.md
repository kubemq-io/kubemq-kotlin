# Compatibility Matrix

## Language and Runtime

| Requirement | Minimum | Recommended | Tested |
|-------------|---------|-------------|--------|
| Kotlin | 2.0 | 2.1+ | 2.1.20 |
| JDK | 11 | 17 | 11, 17, 21 |
| Gradle | 8.0 | 8.12+ | 8.12 |

## Dependencies

| Dependency | Version | Notes |
|------------|---------|-------|
| gRPC | 1.71.0+ | gRPC-Kotlin 1.4.3 for coroutine stubs |
| Protobuf | 4.29.3+ | Protocol Buffers runtime |
| Kotlin Coroutines | 1.10.2+ | Core coroutine support |
| kotlin-logging | 7.0.3+ | SLF4J-based logging |
| OpenTelemetry | 1.46.0+ | Optional, for tracing and metrics |

## Spring Boot Integration

| Requirement | Version |
|-------------|---------|
| Spring Boot | 3.4.0+ |
| Spring Framework | 6.2+ |
| Kotlin Spring Plugin | 2.0+ |

## KubeMQ Server

| Server Version | Supported |
|----------------|-----------|
| 2.x | Yes |
| 3.x | Yes |

## Platform Support

The SDK runs on any platform that supports the JVM:

- Linux (x86_64, aarch64)
- macOS (x86_64, aarch64/Apple Silicon)
- Windows (x86_64)

## Transport

- gRPC over HTTP/2
- Optional TLS (mutual TLS supported)
- Token-based authentication via `KUBEMQ_AUTH_TOKEN`
- Automatic reconnection with configurable backoff

## Known Limitations

- JDK 8 is not supported (minimum JDK 11)
- Kotlin/Native and Kotlin/JS are not supported (JVM only)
- Android is not officially supported but may work on API 26+
