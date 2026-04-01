# Getting Started with the KubeMQ Kotlin SDK

This guide walks you through installing the SDK, connecting to a KubeMQ broker, and sending your first message -- all in under 5 minutes.

## Prerequisites

- **Kotlin** 2.0+ with JDK 11, 17, or 21
- **Gradle** 8.0+ (Kotlin DSL recommended)
- **KubeMQ broker** running locally or in Kubernetes
  - Docker: `docker run -d -p 50000:50000 kubemq/kubemq-community:latest`

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.kubemq.sdk:kubemq-sdk-kotlin:1.0.0")
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

## Step 1: Connect to the Broker

```kotlin
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.client.LogLevel

suspend fun main() {
    val pubsub = KubeMQClient.pubSub {
        address = "localhost:50000"
        clientId = "getting-started"
        logLevel = LogLevel.INFO
    }

    // Verify connectivity
    val info = pubsub.ping()
    println("Connected to ${info.host} v${info.version}")
}
```

## Step 2: Publish an Event

```kotlin
import io.kubemq.sdk.pubsub.eventMessage

suspend fun publishExample() {
    val pubsub = KubeMQClient.pubSub {
        address = "localhost:50000"
    }

    pubsub.publishEvent(eventMessage {
        channel = "events.hello"
        metadata = "greeting"
        body = "Hello, KubeMQ!".toByteArray()
    })

    println("Event published!")
    pubsub.close()
}
```

## Step 3: Subscribe to Events

```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope

suspend fun subscribeExample() = coroutineScope {
    val pubsub = KubeMQClient.pubSub {
        address = "localhost:50000"
    }

    // Start subscriber in a coroutine
    val job = launch {
        pubsub.subscribeToEvents {
            channel = "events.hello"
        }.collect { event ->
            println("Received: ${String(event.body)} from ${event.fromClientId}")
        }
    }

    // Give subscriber time to start
    delay(1000)

    // Publish a message
    pubsub.publishEvent(eventMessage {
        channel = "events.hello"
        body = "Hello from publisher!".toByteArray()
    })

    delay(2000)
    job.cancel()
    pubsub.close()
}
```

## Step 4: Send a Queue Message

```kotlin
import io.kubemq.sdk.queues.queueMessage

suspend fun queueExample() {
    val queues = KubeMQClient.queues {
        address = "localhost:50000"
    }

    // Send a message
    val result = queues.sendQueuesMessage(queueMessage {
        channel = "queues.tasks"
        body = "Process order #123".toByteArray()
    })
    println("Sent: ${result.messageId}")

    // Receive messages
    val response = queues.receiveQueuesMessages {
        channel = "queues.tasks"
        maxItems = 10
        waitTimeoutMs = 5000
        autoAck = true
    }

    for (msg in response.messages) {
        println("Received: ${String(msg.body)}")
    }

    queues.close()
}
```

## Step 5: Send a Command

```kotlin
import io.kubemq.sdk.cq.commandMessage

suspend fun commandExample() = coroutineScope {
    val cq = KubeMQClient.cq {
        address = "localhost:50000"
    }

    // Start command handler
    val handler = launch {
        cq.subscribeToCommands {
            channel = "commands.greet"
        }.collect { cmd ->
            println("Received command: ${cmd.metadata}")
            val reply = cmd.respond {
                executed = true
                metadata = "Hello back!"
            }
            cq.sendCommandResponse(reply)
        }
    }

    delay(1000)

    // Send a command
    val response = cq.sendCommand(commandMessage {
        channel = "commands.greet"
        metadata = "Hi there"
        timeoutMs = 10_000
    })
    println("Command executed: ${response.executed}")

    handler.cancel()
    cq.close()
}
```

## Step 6: Use Events Store with Replay

```kotlin
import io.kubemq.sdk.pubsub.eventStoreMessage
import io.kubemq.sdk.pubsub.StartPosition

suspend fun eventsStoreExample() = coroutineScope {
    val pubsub = KubeMQClient.pubSub {
        address = "localhost:50000"
    }

    // Publish a persistent event
    val result = pubsub.publishEventStore(eventStoreMessage {
        channel = "events-store.demo"
        body = "Persistent message".toByteArray()
    })
    println("Stored: id=${result.id}, sent=${result.sent}")

    // Subscribe and replay from the beginning
    val job = launch {
        pubsub.subscribeToEventsStore {
            channel = "events-store.demo"
            startPosition = StartPosition.StartFromFirst
        }.collect { event ->
            println("Replayed: sequence=${event.sequence}, body=${String(event.body)}")
        }
    }

    delay(3000)
    job.cancel()
    pubsub.close()
}
```

## Step 7: Send a Query with Response Data

```kotlin
import io.kubemq.sdk.cq.queryMessage

suspend fun queryExample() = coroutineScope {
    val cq = KubeMQClient.cq {
        address = "localhost:50000"
    }

    // Start query handler
    val handler = launch {
        cq.subscribeToQueries {
            channel = "queries.lookup"
        }.collect { query ->
            val reply = query.respond {
                executed = true
                body = """{"name": "Alice", "age": 30}""".toByteArray()
            }
            cq.sendQueryResponse(reply)
        }
    }

    delay(1000)

    // Send query and get response with data
    val response = cq.sendQuery(queryMessage {
        channel = "queries.lookup"
        body = """{"id": 42}""".toByteArray()
        timeoutMs = 10_000
    })
    println("Query result: ${String(response.body)}")

    handler.cancel()
    cq.close()
}
```

## Next Steps

- [Concepts Guide](concepts.md) -- Understand KubeMQ messaging patterns
- [Error Handling](how-to/error-handling.md) -- Handle errors gracefully
- [TLS Setup](how-to/tls.md) -- Secure your connections
- [API Reference](https://kubemq.github.io/kubemq-kotlin/) -- Full KDoc documentation
- [Examples](../examples/) -- Complete working examples for all patterns

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KUBEMQ_ADDRESS` | Broker gRPC address | `localhost:50000` |
| `KUBEMQ_CLIENT_ID` | Client identifier | auto-generated |
| `KUBEMQ_AUTH_TOKEN` | Authentication token | empty |
| `KUBEMQ_TLS_CERT_FILE` | TLS client certificate | none |
| `KUBEMQ_TLS_KEY_FILE` | TLS client key | none |
| `KUBEMQ_TLS_CA_FILE` | TLS CA certificate | none |
| `KUBEMQ_LOG_LEVEL` | Log level | `INFO` |
