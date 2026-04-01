# Troubleshooting Guide

Common issues and their solutions when using the KubeMQ Kotlin SDK.

## Connection Refused

**Symptom:** `KubeMQException.Connection: io exception` or `UNAVAILABLE`

**Causes and Solutions:**

1. **Broker not running**
   ```bash
   # Check if KubeMQ is running
   docker ps | grep kubemq
   # Start it
   docker run -d -p 50000:50000 kubemq/kubemq-community:latest
   ```

2. **Wrong address**
   ```kotlin
   // Verify address matches your broker
   val client = KubeMQClient.pubSub {
       address = "localhost:50000" // default gRPC port
   }
   ```

3. **Firewall blocking port 50000**
   ```bash
   # Test connectivity
   nc -zv localhost 50000
   ```

4. **Kubernetes service not exposed**
   ```bash
   kubectl port-forward svc/kubemq-cluster 50000:50000
   ```

## Authentication Errors

**Symptom:** `KubeMQException.Authentication: Unauthenticated`

**Solution:** Provide a valid auth token:
```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    authToken = "your-token-here"
}
```

Or via environment variable:
```bash
export KUBEMQ_AUTH_TOKEN=your-token-here
```

## Timeout Errors

**Symptom:** `KubeMQException.Timeout: Deadline exceeded`

**For commands/queries:** Increase the timeout:
```kotlin
val response = cq.sendCommand(commandMessage {
    channel = "commands.slow-op"
    timeoutMs = 30_000 // 30 seconds instead of default 60s
})
```

**For queue receive:** Increase wait time:
```kotlin
val response = queues.receiveQueuesMessages {
    channel = "queues.tasks"
    waitTimeoutMs = 30_000
}
```

## Message Not Delivered

**Symptom:** Subscriber does not receive messages.

**Checklist:**
1. Publisher and subscriber use the exact same channel name
2. For events: subscriber must be connected BEFORE the event is published
3. For events store: check `startPosition` -- `StartNewOnly` ignores old messages
4. Consumer groups: message goes to ONE member, not all
5. Wildcards: only supported for Events, not Events Store/Queues/CQ

## Queue Messages Not Acknowledged

**Symptom:** Messages keep reappearing after processing.

**Solution:** Ensure you call `ack()` on each received message:
```kotlin
val response = queues.receiveQueuesMessages {
    channel = "queues.tasks"
    autoAck = false // manual ack mode
}
for (msg in response.messages) {
    try {
        process(msg)
        msg.ack() // MUST call this
    } catch (e: Exception) {
        msg.reject() // return to queue
    }
}
```

Or use `autoAck = true` for automatic acknowledgment.

## Transaction Already Completed

**Symptom:** `IllegalStateException: Transaction already completed`

**Cause:** Calling `ack()`, `reject()`, or `reQueue()` more than once on the same message.

**Solution:** Each message can only be completed once:
```kotlin
// WRONG: double ack
msg.ack()
msg.ack() // throws IllegalStateException

// RIGHT: ack once
msg.ack()
```

## Debug Logging

Enable debug logging to see detailed internal operations:

```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    logLevel = LogLevel.DEBUG
}
```

### SLF4J Backend Configuration

The SDK uses SLF4J for logging. Add an SLF4J backend to see output:

**Gradle:**
```kotlin
dependencies {
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}
```

**Configure slf4j-simple** via `src/main/resources/simplelogger.properties`:
```properties
org.slf4j.simpleLogger.defaultLogLevel=debug
org.slf4j.simpleLogger.log.io.kubemq.sdk=debug
```

### What DEBUG Output Shows

- Connection establishment and transport setup
- gRPC channel state transitions
- Message send/receive operations with channel names
- Reconnection attempts and backoff timing
- Subscription registration and stream lifecycle
- Buffer operations (if buffering is enabled)

### TRACE Level

For maximum verbosity (includes gRPC frame details):
```kotlin
logLevel = LogLevel.TRACE
```

## Reconnection Issues

**Symptom:** Client does not reconnect after broker restart.

**Check reconnection config:**
```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    reconnection {
        maxRetries = 0 // 0 = unlimited
        initialBackoffMs = 1000
        maxBackoffMs = 30_000
        jitter = JitterType.FULL
    }
}
```

**Monitor reconnection events:**
```kotlin
launch {
    client.reconnectionEvents.collect { event ->
        println("Reconnected after ${event.attempt} attempts at ${event.timestamp}")
    }
}
```

See [Reconnection Guide](how-to/reconnection.md) for details.

## Events Store Replay Issues

**Symptom:** Events store subscriber does not receive historical messages.

**Check your start position:**
```kotlin
// StartNewOnly (default) -- will NOT replay historical messages
pubsub.subscribeToEventsStore {
    channel = "events-store.orders"
    startPosition = StartPosition.StartNewOnly // only new messages
}

// Use StartFromFirst to replay all messages
pubsub.subscribeToEventsStore {
    channel = "events-store.orders"
    startPosition = StartPosition.StartFromFirst // replay everything
}
```

## Command/Query No Responder

**Symptom:** `KubeMQException.Timeout` when sending a command or query.

**Checklist:**
1. A subscriber must be listening on the same channel BEFORE the command/query is sent
2. The subscriber must call `sendCommandResponse()` or `sendQueryResponse()` within the timeout
3. Check that the subscriber is not in a different consumer group than expected
4. Verify the channel name matches exactly (no wildcards allowed for CQ)

## gRPC Max Message Size

**Symptom:** `RESOURCE_EXHAUSTED: Compressed gRPC message exceeds maximum size`

**Solution:** Increase the max receive size:
```kotlin
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    maxReceiveSize = 200 * 1024 * 1024 // 200 MB
}
```

## Common Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| `Client not connected` | Client closed or not initialized | Check client lifecycle |
| `Client is closed` | Operating on a closed client | Create a new client |
| `Channel cannot be empty` | Missing channel name | Set `channel` property |
| `Queue message must have at least one of: metadata, body, or tags` | Empty message | Add body or metadata |
| `maxItems must be > 0` | Invalid receive config | Set `maxItems >= 1` |
| `Auto-acked message, operations are not allowed` | Trying to ack a simple-API message | Use stream API with `autoAck = false` |

## Related

- [Error Catalog](errors.md) -- Complete exception type reference
- [Error Handling Guide](how-to/error-handling.md) -- Code patterns
- [Reconnection Guide](how-to/reconnection.md) -- Auto-reconnection setup
- [Getting Started](getting-started.md) -- Initial setup
