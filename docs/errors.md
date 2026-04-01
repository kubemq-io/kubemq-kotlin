# Error Catalog

This document catalogs all KubeMQ SDK exception types, their gRPC status code mappings, and recommended recovery actions.

## Exception Hierarchy

All SDK exceptions extend `KubeMQException`, a sealed class enabling exhaustive `when` matching:

```kotlin
catch (e: KubeMQException) {
    when (e) {
        is KubeMQException.Connection -> handleConnectionError(e)
        is KubeMQException.Authentication -> handleAuthError(e)
        is KubeMQException.Authorization -> handlePermissionError(e)
        is KubeMQException.Timeout -> handleTimeout(e)
        is KubeMQException.Validation -> handleValidation(e)
        is KubeMQException.Throttling -> handleRateLimit(e)
        is KubeMQException.Transport -> handleTransportError(e)
        is KubeMQException.Server -> handleServerError(e)
        is KubeMQException.ClientClosed -> handleClientClosed(e)
        is KubeMQException.StreamBroken -> handleStreamBroken(e)
    }
}
```

## Exception Types

| Exception | ErrorCode | ErrorCategory | Retryable | gRPC Status | Description |
|-----------|-----------|---------------|:---------:|-------------|-------------|
| `Connection` | `UNAVAILABLE` | `TRANSIENT` | Yes | `UNAVAILABLE` | Broker unreachable (network error, server down) |
| `Authentication` | `UNAUTHENTICATED` | `PERMANENT` | No | `UNAUTHENTICATED` | Invalid or missing auth token |
| `Authorization` | `PERMISSION_DENIED` | `PERMANENT` | No | `PERMISSION_DENIED` | Insufficient permissions for operation |
| `Timeout` | `DEADLINE_EXCEEDED` | `TRANSIENT` | Yes | `DEADLINE_EXCEEDED` | Operation exceeded deadline |
| `Validation` | `INVALID_ARGUMENT` | `PERMANENT` | No | `INVALID_ARGUMENT`, `ALREADY_EXISTS`, `FAILED_PRECONDITION`, `OUT_OF_RANGE` | Invalid input parameters |
| `Throttling` | `RESOURCE_EXHAUSTED` | `TRANSIENT` | Yes | `RESOURCE_EXHAUSTED` | Rate limited by broker |
| `Transport` | `INTERNAL` | `FATAL` | No | `INTERNAL`, `DATA_LOSS` | Low-level gRPC transport failure |
| `Server` | `UNKNOWN` | `FATAL` | No | `OK`, `CANCELLED`, `UNKNOWN`, `NOT_FOUND`, `ABORTED`, `UNIMPLEMENTED` | Server-side error |
| `ClientClosed` | `CANCELLED` | `FATAL` | No | `CANCELLED` (client-initiated) | Client has been closed |
| `StreamBroken` | `UNAVAILABLE` | `TRANSIENT` | Yes | -- | gRPC stream unexpectedly terminated |

## Common Properties

Every `KubeMQException` includes these properties for programmatic error handling:

```kotlin
catch (e: KubeMQException) {
    println("Code: ${e.code}")           // ErrorCode enum value
    println("Category: ${e.category}")   // TRANSIENT, PERMANENT, or FATAL
    println("Retryable: ${e.isRetryable}") // true if safe to retry
    println("Operation: ${e.operation}") // e.g., "sendCommand", "publishEvent"
    println("Channel: ${e.channel}")     // target channel (if applicable)
    println("Request ID: ${e.requestId}") // request correlation ID (if applicable)
    println("Server: ${e.serverAddress}") // broker address (if applicable)
    println("Message: ${e.message}")     // human-readable description
    println("Cause: ${e.cause}")         // underlying exception (if any)
}
```

## Detailed Exception Descriptions

### Connection

Thrown when the broker is unreachable due to network errors, server down, or DNS resolution failure.

```kotlin
public class Connection(
    message: String,
    cause: Throwable? = null,
    operation: String? = null,
    serverAddress: String? = null,
) : KubeMQException(
    message, cause, ErrorCode.UNAVAILABLE, ErrorCategory.TRANSIENT, true,
    operation, serverAddress = serverAddress,
)
```

**Properties:** `serverAddress` contains the broker address that was unreachable.

### Authentication

Thrown when the auth token is missing, invalid, or expired.

```kotlin
public class Authentication(
    message: String,
    cause: Throwable? = null,
    operation: String? = null,
    serverAddress: String? = null,
) : KubeMQException(
    message, cause, ErrorCode.UNAUTHENTICATED, ErrorCategory.PERMANENT, false,
    operation, serverAddress = serverAddress,
)
```

### Authorization

Thrown when the client lacks permissions for the requested operation on a channel.

```kotlin
public class Authorization(
    message: String,
    cause: Throwable? = null,
    operation: String? = null,
    channel: String? = null,
) : KubeMQException(
    message, cause, ErrorCode.PERMISSION_DENIED, ErrorCategory.PERMANENT, false,
    operation, channel,
)
```

**Properties:** `channel` identifies the channel where access was denied.

### Timeout

Thrown when an operation exceeds its deadline (commands, queries, or gRPC unary calls).

```kotlin
public class Timeout(
    message: String,
    cause: Throwable? = null,
    public val duration: kotlin.time.Duration? = null,
    operation: String? = null,
    channel: String? = null,
    requestId: String? = null,
) : KubeMQException(
    message, cause, ErrorCode.DEADLINE_EXCEEDED, ErrorCategory.TRANSIENT, true,
    operation, channel, requestId,
)
```

**Properties:** `duration` is the timeout that was exceeded; `requestId` correlates to the original request.

### Validation

Thrown for invalid input parameters: blank channels, wildcard misuse, empty messages, or precondition failures.

```kotlin
public class Validation(
    message: String,
    code: ErrorCode = ErrorCode.INVALID_ARGUMENT,
    operation: String? = null,
    channel: String? = null,
) : KubeMQException(
    message, null, code, ErrorCategory.PERMANENT, false,
    operation, channel,
)
```

**Note:** The `code` may be `INVALID_ARGUMENT`, `ALREADY_EXISTS`, `FAILED_PRECONDITION`, or `OUT_OF_RANGE` depending on the specific validation failure.

### Throttling

Thrown when the broker rate-limits the client due to resource exhaustion.

```kotlin
public class Throttling(
    message: String,
    cause: Throwable? = null,
    operation: String? = null,
    channel: String? = null,
) : KubeMQException(
    message, cause, ErrorCode.RESOURCE_EXHAUSTED, ErrorCategory.TRANSIENT, true,
    operation, channel,
)
```

### Transport

Thrown for low-level gRPC transport failures or data corruption.

```kotlin
public class Transport(
    message: String,
    cause: Throwable? = null,
    code: ErrorCode = ErrorCode.INTERNAL,
    operation: String? = null,
) : KubeMQException(
    message, cause, code, ErrorCategory.FATAL, false,
    operation,
)
```

**Note:** The `code` may be `INTERNAL` or `DATA_LOSS`.

### Server

Thrown for server-side errors not covered by more specific exception types.

```kotlin
public class Server(
    message: String,
    cause: Throwable? = null,
    public val statusCode: Int? = null,
    code: ErrorCode = ErrorCode.UNKNOWN,
    category: ErrorCategory = ErrorCategory.FATAL,
    isRetryable: Boolean = false,
    operation: String? = null,
    channel: String? = null,
) : KubeMQException(
    message, cause, code, category, isRetryable,
    operation, channel,
)
```

**Properties:** `statusCode` provides additional server-specific error codes. Note that `ABORTED` status maps to a retryable `Server` exception with `TRANSIENT` category.

### ClientClosed

Thrown when attempting to use a client that has been closed.

```kotlin
public class ClientClosed(
    message: String = "Client is closed",
) : KubeMQException(
    message, null, ErrorCode.CANCELLED, ErrorCategory.FATAL, false,
)
```

### StreamBroken

Thrown when a gRPC bidirectional stream is unexpectedly terminated.

```kotlin
public class StreamBroken(
    message: String,
    cause: Throwable? = null,
    operation: String? = null,
    channel: String? = null,
) : KubeMQException(
    message, cause, ErrorCode.UNAVAILABLE, ErrorCategory.TRANSIENT, true,
    operation, channel,
)
```

## gRPC Status Code Mapping

Source: `GrpcErrorMapper.kt`

| gRPC Status | KubeMQ Exception | Retryable |
|-------------|-----------------|:---------:|
| `OK` | `Server` (unexpected) | No |
| `CANCELLED` (client) | `ClientClosed` | No |
| `CANCELLED` (server) | `Server` | No |
| `UNKNOWN` | `Server` | No |
| `INVALID_ARGUMENT` | `Validation` | No |
| `DEADLINE_EXCEEDED` | `Timeout` | Yes |
| `NOT_FOUND` | `Server` (NOT_FOUND code) | No |
| `ALREADY_EXISTS` | `Validation` (ALREADY_EXISTS code) | No |
| `PERMISSION_DENIED` | `Authorization` | No |
| `RESOURCE_EXHAUSTED` | `Throttling` | Yes |
| `FAILED_PRECONDITION` | `Validation` (FAILED_PRECONDITION code) | No |
| `ABORTED` | `Server` (TRANSIENT, retryable) | Yes |
| `OUT_OF_RANGE` | `Validation` (OUT_OF_RANGE code) | No |
| `UNIMPLEMENTED` | `Server` (UNIMPLEMENTED code) | No |
| `INTERNAL` | `Transport` | No |
| `UNAVAILABLE` | `Connection` | Yes |
| `DATA_LOSS` | `Transport` (DATA_LOSS code) | No |
| `UNAUTHENTICATED` | `Authentication` | No |

## ErrorCode Enum Values

| Value | Description |
|-------|-------------|
| `OK` | Operation succeeded (should not appear in exceptions) |
| `CANCELLED` | Operation cancelled by client or server |
| `UNKNOWN` | Unknown error |
| `INVALID_ARGUMENT` | Invalid input parameter |
| `DEADLINE_EXCEEDED` | Operation timed out |
| `NOT_FOUND` | Resource not found |
| `ALREADY_EXISTS` | Resource already exists |
| `PERMISSION_DENIED` | Insufficient permissions |
| `RESOURCE_EXHAUSTED` | Rate limit exceeded |
| `FAILED_PRECONDITION` | System not in required state |
| `ABORTED` | Concurrency conflict |
| `OUT_OF_RANGE` | Value out of valid range |
| `UNIMPLEMENTED` | Operation not supported |
| `INTERNAL` | Internal server error |
| `UNAVAILABLE` | Service unavailable |
| `DATA_LOSS` | Data corruption or loss |
| `UNAUTHENTICATED` | Missing or invalid credentials |

## ErrorCategory Enum Values

| Value | Description | Action |
|-------|-------------|--------|
| `TRANSIENT` | Temporary error | Retry with backoff |
| `PERMANENT` | Invalid request | Fix input and retry |
| `FATAL` | Unrecoverable | Recreate client or escalate |

## Recovery Steps

### Connection Errors
1. Check broker is running: `docker ps` or `kubectl get pods`
2. Verify address and port in `ClientConfig`
3. Check firewall rules and network connectivity
4. The SDK will automatically retry with exponential backoff

### Authentication Errors
1. Verify `authToken` in `ClientConfig` or `KUBEMQ_AUTH_TOKEN` env var
2. Ensure the token has not expired
3. Contact your KubeMQ administrator for a valid token

### Authorization Errors
1. Check that the client has permissions for the target channel
2. Verify access control rules in the KubeMQ dashboard
3. Contact your KubeMQ administrator

### Timeout Errors
1. Increase `timeoutMs` on the message (commands/queries)
2. Check broker load and response handler performance
3. Ensure the responder is subscribed and processing requests

### Validation Errors
1. Check channel name is not blank
2. Ensure channel does not contain wildcards (where prohibited)
3. Verify message has at least body, metadata, or tags

### Throttling Errors
1. Reduce message send rate
2. Implement backoff between retries
3. Contact your KubeMQ administrator to adjust rate limits

## Related

- [Error Handling Guide](how-to/error-handling.md) -- Patterns for handling errors in code
- [Troubleshooting](troubleshooting.md) -- Common issues and solutions
- [API Reference: KubeMQException](https://kubemq.github.io/kubemq-kotlin/io.kubemq.sdk.exception/-kube-m-q-exception/)
