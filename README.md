# KubeMQ Kotlin SDK

[![CI](https://github.com/kubemq-io/kubemq-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/kubemq-io/kubemq-kotlin/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.kubemq.sdk/kubemq-sdk-kotlin)](https://central.sonatype.com/artifact/io.kubemq.sdk/kubemq-sdk-kotlin)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org)
[![API Docs](https://img.shields.io/badge/docs-KDoc-blue)](https://kubemq.github.io/kubemq-kotlin/)

A Kotlin-first SDK for [KubeMQ](https://kubemq.io) message broker, built with coroutines, Flow, and DSL builders.

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Connection Configuration](#connection-configuration)
- [PubSub](#pubsub)
- [Queues](#queues)
- [Commands and Queries](#commands-and-queries)
- [Channel Management](#channel-management)
- [Requirements](#requirements)
- [Modules](#modules)
- [Documentation](#documentation)
- [Support](#support)
- [License](#license)

## Features

- **PubSub** -- Events and Events Store with streaming support
- **Queues** -- Stream API (upstream/downstream) and Simple API
- **CQ** -- Commands and Queries with request/response pattern
- **Kotlin Coroutines** -- Fully suspending API with Flow-based subscriptions
- **DSL Builders** -- Idiomatic Kotlin builders for all message types
- **Connection Lifecycle** -- Automatic reconnection with configurable backoff
- **Observability** -- Optional OpenTelemetry tracing and metrics
- **BOM Module** -- Dependency management for consistent versions

## Quick Start

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.kubemq.sdk:kubemq-sdk-kotlin:1.0.0")
}
```

Or use the BOM for version alignment:

```kotlin
dependencies {
    implementation(platform("io.kubemq.sdk:kubemq-sdk-kotlin-bom:1.0.0"))
    implementation("io.kubemq.sdk:kubemq-sdk-kotlin")
}
```

### Maven

```xml
<dependency>
    <groupId>io.kubemq.sdk</groupId>
    <artifactId>kubemq-sdk-kotlin</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Connection Configuration

```kotlin
// Using DSL builder
val client = KubeMQClient.pubSub {
    address = "localhost:50000"
    clientId = "my-app"
    logLevel = LogLevel.INFO
}

// From environment variables
// Set KUBEMQ_ADDRESS, KUBEMQ_CLIENT_ID, KUBEMQ_AUTH_TOKEN
val client = KubeMQClient.pubSub {
    // address defaults to KUBEMQ_ADDRESS env var or localhost:50000
}
```

### Configuration Reference

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `address` | `KUBEMQ_ADDRESS` | `localhost:50000` | Broker gRPC address |
| `clientId` | `KUBEMQ_CLIENT_ID` | auto-generated | Unique client identifier |
| `authToken` | `KUBEMQ_AUTH_TOKEN` | `""` | Bearer token for authentication |
| `logLevel` | - | `INFO` | Logging verbosity |
| `tls` | - | disabled | TLS configuration (cert, key, ca) |
| `maxReceiveSize` | - | `104857600` | Max inbound message size (bytes) |
| `keepAlive` | - | enabled | gRPC keep-alive settings |
| `reconnectionConfig` | - | enabled | Auto-reconnection with backoff |

## PubSub

### Publish Events

```kotlin
val pubsub = KubeMQClient.pubSub {
    address = "localhost:50000"
}

pubsub.publishEvent(eventMessage {
    channel = "events.temperature"
    metadata = "sensor-1"
    body = """{"temp": 23.5}""".toByteArray()
})
```

### Subscribe to Events

```kotlin
pubsub.subscribeToEvents {
    channel = "events.temperature"
    group = "processors"
}.collect { event ->
    println("Received: ${event.metadata} on ${event.channel}")
}
```

### Events Store

```kotlin
// Publish
pubsub.publishEventStore(eventStoreMessage {
    channel = "events-store.orders"
    body = orderJson.toByteArray()
})

// Subscribe from beginning
pubsub.subscribeToEventsStore {
    channel = "events-store.orders"
    startPosition = StartPosition.StartFromFirst
}.collect { event ->
    println("Replayed: ${event.id}")
}
```

## Queues

### Stream API

```kotlin
val queues = KubeMQClient.queues {
    address = "localhost:50000"
}

// Send
queues.sendQueuesMessage(queueMessage {
    channel = "queues.tasks"
    body = "process-this".toByteArray()
})

// Receive with auto-ack
val response = queues.receiveQueuesMessages {
    channel = "queues.tasks"
    maxItems = 10
    waitTimeoutMs = 5000
    autoAck = true
}

for (msg in response.messages) {
    println("Task: ${String(msg.body)}")
}
```

### Simple API

```kotlin
// Send
queues.sendQueueMessage(queueMessage {
    channel = "queues.jobs"
    body = "job-data".toByteArray()
})

// Receive
val response = queues.receiveQueueMessages {
    channel = "queues.jobs"
    maxNumberOfMessages = 5
    waitTimeSeconds = 3
}
```

## Commands and Queries

### Commands

```kotlin
val cq = KubeMQClient.cq {
    address = "localhost:50000"
}

// Send a command
val response = cq.sendCommand(commandMessage {
    channel = "commands.shutdown"
    metadata = "graceful"
    timeoutMs = 10_000
})
println("Executed: ${response.executed}")

// Subscribe and respond
cq.subscribeToCommands {
    channel = "commands.shutdown"
    group = "handlers"
}.collect { cmd ->
    val reply = cmd.respond {
        executed = true
        metadata = "done"
    }
    cq.sendCommandResponse(reply)
}
```

### Queries

```kotlin
// Send a query
val response = cq.sendQuery(queryMessage {
    channel = "queries.user-lookup"
    body = """{"id": 42}""".toByteArray()
    timeoutMs = 10_000
})
println("Result: ${String(response.body)}")

// Subscribe and respond
cq.subscribeToQueries {
    channel = "queries.user-lookup"
}.collect { query ->
    val reply = query.respond {
        executed = true
        body = """{"name": "Alice"}""".toByteArray()
    }
    cq.sendQueryResponse(reply)
}
```

## Channel Management

```kotlin
// Create channels
pubsub.createEventsChannel("events.temperature")
pubsub.createEventsStoreChannel("events-store.orders")
queues.createQueuesChannel("queues.tasks")
cq.createCommandsChannel("commands.shutdown")
cq.createQueriesChannel("queries.user-lookup")

// List channels
val channels = pubsub.listEventsChannels()
channels.forEach { println("${it.name}: ${it.lastActivity}") }

// Delete channels
pubsub.deleteEventsChannel("events.temperature")
```

## Requirements

- Kotlin 2.0+
- JDK 11, 17, or 21
- KubeMQ server (locally or in Kubernetes)

## Modules

| Module | Description |
|--------|-------------|
| `kubemq-sdk-kotlin` | Core SDK with all client types |
| `kubemq-sdk-kotlin-bom` | BOM for dependency management |
| `examples` | Example programs for all patterns |
| `burnin` | Burn-in soak test application |

## Documentation

- [Getting Started](docs/getting-started.md)
- [Concepts Guide](docs/concepts.md)
- [Error Catalog](docs/errors.md)
- [Troubleshooting](docs/troubleshooting.md)
- [TLS/mTLS Setup](docs/how-to/tls.md)
- [Error Handling](docs/how-to/error-handling.md)
- [Reconnection](docs/how-to/reconnection.md)
- [Consumer Groups](docs/how-to/consumer-groups.md)
- [API Reference (KDoc)](https://kubemq.github.io/kubemq-kotlin/)

## Support

- [GitHub Issues](https://github.com/kubemq-io/kubemq-kotlin/issues) -- Bug reports and feature requests
- [KubeMQ Documentation](https://docs.kubemq.io) -- Broker documentation
- [KubeMQ Website](https://kubemq.io) -- Product information

## Links

- [KubeMQ Website](https://kubemq.io)
- [KubeMQ Documentation](https://docs.kubemq.io)
- [API Reference (KDoc)](https://kubemq.github.io/kubemq-kotlin/)
- [Examples](examples/)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)
- [Security Policy](SECURITY.md)

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
