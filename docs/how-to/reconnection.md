# How To: Configure Reconnection

This guide explains how to configure automatic reconnection and monitor connection state.

## Prerequisites

- KubeMQ Kotlin SDK installed

## Default Behavior

By default, the SDK automatically reconnects with exponential backoff:

- Initial backoff: 500ms
- Max backoff: 30 seconds
- Multiplier: 2.0x
- Jitter: Full random
- Max retries: Unlimited (0)

## Custom Reconnection Configuration

```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    reconnection {
        initialBackoffMs = 1000     // Start with 1 second
        maxBackoffMs = 60_000       // Cap at 1 minute
        multiplier = 2.0            // Double each time
        maxRetries = 20             // Give up after 20 attempts (0 = unlimited)
        jitter = JitterType.FULL    // Full randomization
    }
}
```

## Jitter Strategies

| Strategy | Delay Formula | Use Case |
|----------|--------------|----------|
| `NONE` | Exact backoff | Testing, predictable behavior |
| `FULL` | Random(0, backoff) | Production (prevents thundering herd) |
| `EQUAL` | backoff/2 + Random(0, backoff/2) | Balanced between predictable and random |

## Monitoring Connection State

Observe the connection state using a `StateFlow`:

```kotlin
import io.kubemq.sdk.client.ConnectionState
import kotlinx.coroutines.launch

val client = KubeMQClient.pubSub {
    address = "localhost:50000"
}

// Monitor in a coroutine
launch {
    client.connectionState.collect { state ->
        when (state) {
            is ConnectionState.Idle -> println("Idle -- no connection yet")
            is ConnectionState.Connecting -> println("Connecting...")
            is ConnectionState.Ready -> println("Connected and ready")
            is ConnectionState.Reconnecting -> {
                println("Reconnecting, attempt #${state.attempt}")
            }
            is ConnectionState.Closed -> println("Client closed permanently")
        }
    }
}
```

## Connection State Transitions

```
Idle --> Connecting --> Ready --> Reconnecting --> Ready
                         |                          |
                         v                          v
                       Closed                     Closed
```

- **Idle**: Initial state before any connection attempt
- **Connecting**: Connection attempt in progress
- **Ready**: Connected and operational
- **Reconnecting**: Lost connection, attempting to reconnect
- **Closed**: Client permanently closed (terminal state)

## Reconnection Events

Get notified after each successful reconnection:

```kotlin
launch {
    client.reconnectionEvents.collect { event ->
        println("Reconnected after ${event.attempt} attempts")
        println("Timestamp: ${event.timestamp}")
    }
}
```

## Message Buffering During Disconnection

Buffer messages during reconnection and replay them automatically:

```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    buffer {
        enabled = true
        maxSize = 500              // Buffer up to 500 messages
        overflowPolicy = BufferOverflowPolicy.DROP_OLDEST
    }
}
```

### Buffer Overflow Policies

| Policy | Behavior |
|--------|----------|
| `REJECT` | Throws exception when buffer is full (default) |
| `DROP_OLDEST` | Drops oldest buffered message |
| `DROP_NEWEST` | Drops the new message silently |
| `BLOCK` | Blocks the calling coroutine until space is available |

## Subscription Auto-Recovery

Active subscriptions are automatically re-established after reconnection. No manual action is needed:

```kotlin
// This subscription survives broker restarts
pubsub.subscribeToEvents {
    channel = "events.data"
}.collect { event ->
    process(event) // continues receiving after reconnection
}
```

## Disabling Reconnection

To disable auto-reconnection (fail immediately on disconnect):

```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    reconnection {
        maxRetries = 1 // only try once
    }
}
```

## Reconnection Backoff Sequence Example

With default settings (`initialBackoffMs = 500`, `multiplier = 2.0`, `maxBackoffMs = 30000`, `jitter = FULL`):

| Attempt | Computed Backoff | With Full Jitter (range) |
|---------|-----------------|--------------------------|
| 1 | 500ms | 0 - 500ms |
| 2 | 1000ms | 0 - 1000ms |
| 3 | 2000ms | 0 - 2000ms |
| 4 | 4000ms | 0 - 4000ms |
| 5 | 8000ms | 0 - 8000ms |
| 6 | 16000ms | 0 - 16000ms |
| 7+ | 30000ms (capped) | 0 - 30000ms |

## Complete Example

```kotlin
import io.kubemq.sdk.client.*
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.*

suspend fun main() = coroutineScope {
    val pubsub = KubeMQClient.pubSub {
        address = "localhost:50000"
        reconnection {
            initialBackoffMs = 1000
            maxBackoffMs = 30_000
            maxRetries = 0 // unlimited
            jitter = JitterType.FULL
        }
        buffer {
            enabled = true
            maxSize = 1000
            overflowPolicy = BufferOverflowPolicy.DROP_OLDEST
        }
    }

    // Monitor state
    launch {
        pubsub.connectionState.collect { state ->
            println("State: $state")
        }
    }

    // Monitor reconnections
    launch {
        pubsub.reconnectionEvents.collect { event ->
            println("Reconnected! Attempt: ${event.attempt}")
        }
    }

    // Publish messages -- buffered during disconnections
    while (isActive) {
        try {
            pubsub.publishEvent(eventMessage {
                channel = "events.heartbeat"
                body = "alive".toByteArray()
            })
        } catch (e: Exception) {
            println("Send failed (will retry): ${e.message}")
        }
        delay(1000)
    }
}
```

## Reconnection with All Client Types

Reconnection configuration works identically across all client types:

```kotlin
// Queues client with reconnection
val queues = KubeMQClient.queues {
    address = "localhost:50000"
    reconnection {
        initialBackoffMs = 1000
        maxRetries = 10
    }
}

// CQ client with reconnection
val cq = KubeMQClient.cq {
    address = "localhost:50000"
    reconnection {
        initialBackoffMs = 1000
        maxRetries = 10
    }
}
```

## Related

- [API Reference: ReconnectionConfig](https://kubemq.github.io/kubemq-kotlin/io.kubemq.sdk.client/-reconnection-config/)
- [API Reference: ConnectionState](https://kubemq.github.io/kubemq-kotlin/io.kubemq.sdk.client/-connection-state/)
- [Error Handling Guide](error-handling.md)
- [Troubleshooting](../troubleshooting.md)
