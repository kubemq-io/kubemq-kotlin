# How To: Handle Errors

This guide explains the KubeMQ Kotlin SDK error handling patterns, typed exception matching, and retry strategies.

## Prerequisites

- KubeMQ Kotlin SDK installed
- Familiarity with Kotlin sealed classes and `when` expressions

## Typed Exception Matching

All SDK exceptions extend `KubeMQException`, a sealed class. Use `when` for exhaustive matching:

```kotlin
import io.kubemq.sdk.exception.KubeMQException

try {
    val response = cq.sendCommand(commandMessage {
        channel = "commands.process"
        timeoutMs = 10_000
    })
} catch (e: KubeMQException) {
    when (e) {
        is KubeMQException.Connection -> {
            println("Connection lost: ${e.message}")
            println("Server: ${e.serverAddress}")
            // SDK will auto-reconnect; retry the operation
        }
        is KubeMQException.Timeout -> {
            println("Timed out after ${e.duration}")
            println("Request: ${e.requestId}")
            // Retry with a longer timeout
        }
        is KubeMQException.Authentication -> {
            println("Auth failed: ${e.message}")
            // Fix credentials and retry
        }
        is KubeMQException.Authorization -> {
            println("No permission for channel: ${e.channel}")
            // Contact administrator
        }
        is KubeMQException.Validation -> {
            println("Invalid input: ${e.message}")
            // Fix the request parameters
        }
        is KubeMQException.Throttling -> {
            println("Rate limited: ${e.message}")
            // Back off and retry
        }
        is KubeMQException.Transport -> {
            println("Transport error: ${e.message}")
            // Fatal -- may need to recreate client
        }
        is KubeMQException.Server -> {
            println("Server error: ${e.message}, code: ${e.statusCode}")
            // Check server logs
        }
        is KubeMQException.ClientClosed -> {
            println("Client is closed")
            // Create a new client
        }
        is KubeMQException.StreamBroken -> {
            println("Stream broken: ${e.message}")
            // SDK will auto-reconnect
        }
    }
}
```

## Using Error Properties

Every `KubeMQException` provides structured error information:

```kotlin
catch (e: KubeMQException) {
    println("Code: ${e.code}")           // ErrorCode enum
    println("Category: ${e.category}")   // TRANSIENT, PERMANENT, FATAL
    println("Retryable: ${e.isRetryable}")
    println("Operation: ${e.operation}") // e.g., "sendCommand"
    println("Channel: ${e.channel}")     // if applicable
    println("Message: ${e.message}")
}
```

## Category-Based Handling

For simpler error handling, use the error category:

```kotlin
import io.kubemq.sdk.exception.ErrorCategory

catch (e: KubeMQException) {
    when (e.category) {
        ErrorCategory.TRANSIENT -> {
            // Safe to retry with backoff
            delay(1000)
            retry()
        }
        ErrorCategory.PERMANENT -> {
            // Fix the input and try again
            log.error("Invalid request: ${e.message}")
        }
        ErrorCategory.FATAL -> {
            // Recreate client or escalate
            log.error("Fatal error: ${e.message}")
            client.close()
        }
    }
}
```

## Retryable Check

```kotlin
catch (e: KubeMQException) {
    if (e.isRetryable) {
        delay(1000)
        retry()
    } else {
        throw e
    }
}
```

## Retry Policy

Use the built-in `RetryPolicy` for automatic retries:

```kotlin
import io.kubemq.sdk.retry.retryPolicy
import io.kubemq.sdk.client.JitterType
import io.kubemq.sdk.exception.ErrorCode

val policy = retryPolicy {
    maxAttempts = 5
    initialBackoffMs = 500
    maxBackoffMs = 30_000
    multiplier = 2.0
    jitter = JitterType.FULL
    retryableStatusCodes = setOf(
        ErrorCode.UNAVAILABLE,
        ErrorCode.DEADLINE_EXCEEDED,
        ErrorCode.RESOURCE_EXHAUSTED,
    )
}
```

## Manual Retry with Exponential Backoff

```kotlin
import io.kubemq.sdk.exception.KubeMQException
import kotlinx.coroutines.delay

suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 30_000,
    factor: Double = 2.0,
    block: suspend () -> T,
): T {
    var currentDelay = initialDelayMs
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: KubeMQException) {
            if (!e.isRetryable) throw e
            println("Attempt ${attempt + 1} failed: ${e.message}, retrying in ${currentDelay}ms")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }
    return block() // last attempt -- let exception propagate
}

// Usage
val response = retryWithBackoff {
    cq.sendCommand(commandMessage {
        channel = "commands.process"
        timeoutMs = 10_000
    })
}
```

## PubSub Error Handling

```kotlin
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import io.kubemq.sdk.exception.KubeMQException

suspend fun publishWithRetry(pubsub: PubSubClient) {
    try {
        pubsub.publishEvent(eventMessage {
            channel = "events.orders"
            body = """{"orderId": 123}""".toByteArray()
        })
        println("Event published successfully")
    } catch (e: KubeMQException.Connection) {
        println("Connection lost, SDK will auto-reconnect: ${e.message}")
        // The SDK handles reconnection automatically
    } catch (e: KubeMQException.Validation) {
        println("Invalid message: ${e.message}")
        // Fix the message parameters
    } catch (e: KubeMQException.ClientClosed) {
        println("Client was closed, cannot publish")
        // Need to create a new client
    }
}
```

## Queue Error Handling

Queue operations have additional error states:

```kotlin
// Check send result for errors
val result = queues.sendQueuesMessage(queueMessage {
    channel = "queues.tasks"
    body = "data".toByteArray()
})
if (result.isError) {
    println("Send failed: ${result.error}")
}

// Check receive response for errors
val response = queues.receiveQueuesMessages {
    channel = "queues.tasks"
    maxItems = 10
    waitTimeoutMs = 5000
}
if (response.isError) {
    println("Receive failed: ${response.error}")
}

// Handle individual message ack/reject errors
for (msg in response.messages) {
    try {
        processMessage(msg)
        msg.ack()
    } catch (e: IllegalStateException) {
        println("Transaction already completed: ${e.message}")
    } catch (e: Exception) {
        try {
            msg.reject()
        } catch (rejectError: Exception) {
            println("Failed to reject message: ${rejectError.message}")
        }
    }
}
```

## Subscription Error Handling

Subscription errors are delivered as Flow exceptions:

```kotlin
try {
    pubsub.subscribeToEvents {
        channel = "events.data"
    }.collect { event ->
        processEvent(event)
    }
} catch (e: KubeMQException.Connection) {
    println("Subscription disconnected: ${e.message}")
} catch (e: KubeMQException.StreamBroken) {
    println("Stream broken: ${e.message}")
}
```

## Handling Errors in Coroutine Scope

When running multiple subscriptions, use a `SupervisorJob` to prevent one failure from cancelling all subscriptions:

```kotlin
import kotlinx.coroutines.*

suspend fun main() = coroutineScope {
    val pubsub = KubeMQClient.pubSub {
        address = "localhost:50000"
    }

    val supervisor = SupervisorJob()

    launch(supervisor) {
        try {
            pubsub.subscribeToEvents {
                channel = "events.orders"
            }.collect { event ->
                println("Order: ${String(event.body)}")
            }
        } catch (e: KubeMQException) {
            println("Orders subscription failed: ${e.message}")
        }
    }

    launch(supervisor) {
        try {
            pubsub.subscribeToEvents {
                channel = "events.notifications"
            }.collect { event ->
                println("Notification: ${String(event.body)}")
            }
        } catch (e: KubeMQException) {
            println("Notifications subscription failed: ${e.message}")
        }
    }
}
```

## Graceful Shutdown

Always close clients when shutting down:

```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
}

Runtime.getRuntime().addShutdownHook(Thread {
    println("Shutting down...")
    client.close()
})
```

Or use Kotlin's `use` pattern if the client implements `Closeable`:

```kotlin
KubeMQClient.pubSub {
    address = "localhost:50000"
}.use { pubsub ->
    pubsub.publishEvent(eventMessage {
        channel = "events.test"
        body = "hello".toByteArray()
    })
} // automatically closed
```

## Related

- [Error Catalog](../errors.md) -- Complete list of exception types and gRPC mappings
- [Troubleshooting Guide](../troubleshooting.md) -- Common issues and solutions
- [Reconnection Guide](reconnection.md) -- Auto-reconnection configuration
