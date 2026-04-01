# KubeMQ Concepts

This guide explains the core messaging concepts in KubeMQ and how they map to the Kotlin SDK.

## Channels

A channel is a named endpoint for message routing. Every message is published to or received from a channel. Channel names are strings and can use dot notation for organization:

```
events.orders.created
queues.payments.pending
commands.inventory.reserve
```

## Messaging Patterns

KubeMQ supports four messaging patterns, each suited for different use cases:

### 1. Events (Fire-and-Forget)

Events are real-time, non-persistent messages delivered to all active subscribers. If no subscriber is connected, the message is lost.

**Use case:** Live telemetry, logging, real-time notifications.

```kotlin
val pubsub = KubeMQClient.pubSub { address = "localhost:50000" }

// Publish
pubsub.publishEvent(eventMessage {
    channel = "events.temperature"
    body = """{"sensor": "A1", "temp": 22.5}""".toByteArray()
})

// Subscribe
pubsub.subscribeToEvents {
    channel = "events.temperature"
}.collect { event ->
    println("Temperature update: ${String(event.body)}")
}
```

**Key properties:**
- Non-persistent (no replay)
- Broadcast to all subscribers (unless using consumer groups)
- Wildcards supported (`events.*`, `events.>`)
- No acknowledgment required

### 2. Events Store (Persistent Events)

Events store messages are persisted by the broker and can be replayed from various positions. Subscribers choose where to start reading.

**Use case:** Audit logs, event sourcing, stream processing.

```kotlin
// Publish (persisted)
pubsub.publishEventStore(eventStoreMessage {
    channel = "events-store.orders"
    body = orderJson.toByteArray()
})

// Subscribe from beginning
pubsub.subscribeToEventsStore {
    channel = "events-store.orders"
    startPosition = StartPosition.StartFromFirst
}.collect { event ->
    println("Order ${event.sequence}: ${String(event.body)}")
}
```

**Start positions:**
| Position | Description |
|----------|-------------|
| `StartNewOnly` | Only new messages after subscribing |
| `StartFromFirst` | Replay from the first message |
| `StartFromLast` | Start from the last message |
| `StartAtSequence(n)` | Start at sequence number `n` |
| `StartAtTime(nanos)` | Start at a specific timestamp |
| `StartAtTimeDelta(secs)` | Start from N seconds ago |

**Key properties:**
- Persistent with replay
- Wildcards NOT supported
- Each message gets a sequence number

### 3. Queues (Guaranteed Delivery)

Queue messages are stored until a consumer acknowledges them. Each message is delivered to exactly one consumer.

**Use case:** Task distribution, work queues, job processing.

```kotlin
val queues = KubeMQClient.queues { address = "localhost:50000" }

// Send
queues.sendQueuesMessage(queueMessage {
    channel = "queues.tasks"
    body = "process-order-123".toByteArray()
    policy = QueueMessagePolicy(
        expirationSeconds = 3600,
        maxReceiveCount = 3,
        maxReceiveQueue = "queues.dead-letter",
    )
})

// Receive with manual ack
val response = queues.receiveQueuesMessages {
    channel = "queues.tasks"
    maxItems = 10
    waitTimeoutMs = 5000
    autoAck = false
}

for (msg in response.messages) {
    try {
        processTask(String(msg.body))
        msg.ack()
    } catch (e: Exception) {
        msg.reject() // return to queue
    }
}
```

**Key properties:**
- Guaranteed at-least-once delivery
- Manual ack/reject/requeue
- Dead-letter queue support
- Message expiration and delayed delivery
- Two APIs: Stream (bidirectional) and Simple (unary)

### 4. Commands (Fire-and-Execute)

Commands follow a request-response pattern where the sender waits for a boolean acknowledgment. Exactly one subscriber handles the command.

**Use case:** Remote procedure calls, control plane operations.

```kotlin
val cq = KubeMQClient.cq { address = "localhost:50000" }

// Send command and wait for response
val response = cq.sendCommand(commandMessage {
    channel = "commands.shutdown"
    metadata = "graceful"
    timeoutMs = 10_000
})
println("Executed: ${response.executed}")
```

### 5. Queries (Request-Response with Data)

Queries are like commands but return a data payload. Responses can be cached by the broker.

**Use case:** Data lookups, aggregations, service-to-service calls.

```kotlin
// Send query
val response = cq.sendQuery(queryMessage {
    channel = "queries.user"
    body = """{"id": 42}""".toByteArray()
    timeoutMs = 10_000
    cacheKey = "user-42"
    cacheTtlSeconds = 60
})
println("User: ${String(response.body)}, cache hit: ${response.cacheHit}")
```

## Consumer Groups

Consumer groups enable load balancing by distributing messages across multiple subscribers within the same group. Each message is delivered to exactly one member of the group.

```kotlin
// Two subscribers in the same group -- each gets a subset of messages
pubsub.subscribeToEvents {
    channel = "events.orders"
    group = "order-processors"
}.collect { event -> /* ... */ }
```

**Applies to:** Events, Events Store, Commands, Queries

Without a group, events are broadcast to ALL subscribers. With a group, each event goes to ONE group member.

## Wildcards

Event subscriptions support wildcard patterns:

| Pattern | Matches |
|---------|---------|
| `events.*` | Any single level: `events.orders`, `events.users` |
| `events.>` | Any number of levels: `events.orders.created`, `events.orders.updated.v2` |

**Note:** Wildcards are supported only for Events subscriptions. Events Store, Queues, Commands, and Queries require exact channel names.

## Connection Lifecycle

The SDK manages connections automatically:

1. **Lazy connect** -- connection is established on first operation
2. **Auto-reconnect** -- exponential backoff with jitter on disconnect
3. **Message buffering** -- optional buffering during reconnection
4. **State monitoring** -- observe connection state via `StateFlow`

```kotlin
// Monitor connection state
client.connectionState.collect { state ->
    when (state) {
        is ConnectionState.Ready -> println("Connected")
        is ConnectionState.Reconnecting -> println("Reconnecting (attempt ${state.attempt})")
        is ConnectionState.Closed -> println("Closed")
        else -> {}
    }
}
```

See [Reconnection Guide](how-to/reconnection.md) for detailed configuration.

## Client Types

The SDK provides three client types, each created via factory methods on `KubeMQClient`:

| Client | Factory | Patterns |
|--------|---------|----------|
| `PubSubClient` | `KubeMQClient.pubSub { }` | Events, Events Store |
| `QueuesClient` | `KubeMQClient.queues { }` | Queues (Stream and Simple API) |
| `CQClient` | `KubeMQClient.cq { }` | Commands, Queries |

All clients share the same configuration DSL and connection lifecycle behavior.

## Channel Management

Every client type supports channel management operations:

```kotlin
// Create
pubsub.createEventsChannel("events.orders")
queues.createQueuesChannel("queues.tasks")
cq.createCommandsChannel("commands.shutdown")

// List
val channels = pubsub.listEventsChannels()
channels.forEach { println("${it.name}: ${it.lastActivity}") }

// Delete
pubsub.deleteEventsChannel("events.orders")
```

## DSL Builders

All message types use Kotlin DSL builders for idiomatic construction:

```kotlin
// Event message builder
val event = eventMessage {
    channel = "events.orders"
    metadata = "order-created"
    body = orderJson.toByteArray()
    tags = mapOf("priority" to "high")
}

// Queue message builder
val queueMsg = queueMessage {
    channel = "queues.tasks"
    body = taskData.toByteArray()
}

// Command message builder
val cmd = commandMessage {
    channel = "commands.process"
    timeoutMs = 10_000
}

// Query message builder
val query = queryMessage {
    channel = "queries.lookup"
    body = requestData.toByteArray()
    timeoutMs = 10_000
}
```

## Related

- [Getting Started](getting-started.md) -- Quick start guide
- [Error Catalog](errors.md) -- Exception types and recovery
- [Consumer Groups Guide](how-to/consumer-groups.md) -- Load balancing setup
- [TLS/mTLS Guide](how-to/tls.md) -- Secure connections
- [API Reference](https://kubemq.github.io/kubemq-kotlin/) -- Full KDoc documentation
