# How To: Use Consumer Groups

This guide explains how to configure consumer groups for load-balanced message delivery across multiple subscribers.

## Prerequisites

- KubeMQ Kotlin SDK installed
- KubeMQ broker running

## What are Consumer Groups?

A consumer group is a named set of subscribers that share message delivery. When a message is published, it is delivered to exactly ONE member of each group. Without a group, messages are broadcast to ALL subscribers.

## Events Consumer Group

```kotlin
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.pubsub.eventMessage
import kotlinx.coroutines.*

suspend fun main() = coroutineScope {
    // Create publisher
    val publisher = KubeMQClient.pubSub {
        address = "localhost:50000"
        clientId = "publisher"
    }

    // Create two subscribers in the same group
    val subscriber1 = KubeMQClient.pubSub {
        address = "localhost:50000"
        clientId = "worker-1"
    }

    val subscriber2 = KubeMQClient.pubSub {
        address = "localhost:50000"
        clientId = "worker-2"
    }

    // Both subscribe to same channel with same group
    val job1 = launch {
        subscriber1.subscribeToEvents {
            channel = "events.orders"
            group = "order-processors" // same group
        }.collect { event ->
            println("Worker-1 got: ${String(event.body)}")
        }
    }

    val job2 = launch {
        subscriber2.subscribeToEvents {
            channel = "events.orders"
            group = "order-processors" // same group
        }.collect { event ->
            println("Worker-2 got: ${String(event.body)}")
        }
    }

    delay(1000) // let subscribers connect

    // Publish 10 messages -- each goes to exactly one worker
    repeat(10) { i ->
        publisher.publishEvent(eventMessage {
            channel = "events.orders"
            body = "Order #$i".toByteArray()
        })
    }

    delay(3000)
    job1.cancel()
    job2.cancel()
    publisher.close()
    subscriber1.close()
    subscriber2.close()
}
```

## Events Store Consumer Group

```kotlin
import io.kubemq.sdk.pubsub.StartPosition

val job = launch {
    subscriber.subscribeToEventsStore {
        channel = "events-store.orders"
        group = "processors"
        startPosition = StartPosition.StartFromFirst
    }.collect { event ->
        println("Processing order ${event.sequence}")
    }
}
```

## Commands Consumer Group

Commands are inherently point-to-point (one handler), but consumer groups let you load-balance across multiple handlers:

```kotlin
import io.kubemq.sdk.cq.commandMessage

// Handler 1
launch {
    cq.subscribeToCommands {
        channel = "commands.process"
        group = "handlers"
    }.collect { cmd ->
        println("Handler-1 processing command ${cmd.id}")
        val reply = cmd.respond { executed = true }
        cq.sendCommandResponse(reply)
    }
}

// Handler 2
launch {
    cq.subscribeToCommands {
        channel = "commands.process"
        group = "handlers"
    }.collect { cmd ->
        println("Handler-2 processing command ${cmd.id}")
        val reply = cmd.respond { executed = true }
        cq.sendCommandResponse(reply)
    }
}

// Each command goes to exactly one handler
cq.sendCommand(commandMessage {
    channel = "commands.process"
    timeoutMs = 10_000
})
```

## Queries Consumer Group

```kotlin
import io.kubemq.sdk.cq.queryMessage

// Multiple query handlers in a group
launch {
    cq.subscribeToQueries {
        channel = "queries.lookup"
        group = "query-handlers"
    }.collect { query ->
        println("Handler processing query ${query.id}")
        val reply = query.respond {
            executed = true
            body = """{"result": "data"}""".toByteArray()
        }
        cq.sendQueryResponse(reply)
    }
}
```

## Queue Consumer Groups

Queues inherently distribute messages -- each message goes to exactly one consumer. Multiple consumers on the same channel act as a consumer group automatically:

```kotlin
import io.kubemq.sdk.queues.queueMessage

// Worker 1
launch {
    while (isActive) {
        val response = queues.receiveQueuesMessages {
            channel = "queues.tasks"
            maxItems = 5
            waitTimeoutMs = 5000
            autoAck = true
        }
        response.messages.forEach { msg ->
            println("Worker-1 processing: ${String(msg.body)}")
        }
    }
}

// Worker 2 (on same channel -- automatic load balancing)
launch {
    while (isActive) {
        val response = queues.receiveQueuesMessages {
            channel = "queues.tasks"
            maxItems = 5
            waitTimeoutMs = 5000
            autoAck = true
        }
        response.messages.forEach { msg ->
            println("Worker-2 processing: ${String(msg.body)}")
        }
    }
}
```

## Multiple Groups on Same Channel

Different groups each receive a copy of every message:

```kotlin
// Group A: order processors (one member gets each message)
launch {
    pubsub.subscribeToEvents {
        channel = "events.orders"
        group = "order-processors"
    }.collect { event ->
        println("Processor: ${String(event.body)}")
    }
}

// Group B: analytics (one member gets each message)
launch {
    pubsub.subscribeToEvents {
        channel = "events.orders"
        group = "analytics"
    }.collect { event ->
        println("Analytics: ${String(event.body)}")
    }
}

// Group C: auditing (one member gets each message)
launch {
    pubsub.subscribeToEvents {
        channel = "events.orders"
        group = "auditing"
    }.collect { event ->
        println("Audit: ${String(event.body)}")
    }
}

// Each published message is delivered to:
// - ONE member of "order-processors"
// - ONE member of "analytics"
// - ONE member of "auditing"
```

## Group vs No Group

| Scenario | Behavior |
|----------|----------|
| Events, no group | Broadcast to ALL subscribers |
| Events, same group | Delivered to ONE group member |
| Events, different groups | Delivered to ONE member per group |
| Events Store, no group | Broadcast to ALL subscribers |
| Events Store, same group | Delivered to ONE group member |
| Commands, no group | Delivered to ONE subscriber |
| Commands, same group | Load-balanced across group members |
| Queues | Always one consumer per message (group is implicit) |

## Scaling Patterns

### Horizontal Scaling

Add more group members to increase throughput:

```kotlin
// Scale from 2 to 5 workers by launching more subscribers
repeat(5) { workerIndex ->
    launch {
        val subscriber = KubeMQClient.pubSub {
            address = "localhost:50000"
            clientId = "worker-$workerIndex"
        }
        subscriber.subscribeToEvents {
            channel = "events.orders"
            group = "order-processors"
        }.collect { event ->
            println("Worker-$workerIndex got: ${String(event.body)}")
        }
    }
}
```

### Mixed Broadcast and Load-Balanced

Combine group and non-group subscribers:

```kotlin
// This subscriber gets ALL messages (no group)
launch {
    pubsub.subscribeToEvents {
        channel = "events.orders"
        // no group = broadcast
    }.collect { event ->
        println("Monitor: ${String(event.body)}")
    }
}

// These subscribers share messages (same group)
launch {
    pubsub.subscribeToEvents {
        channel = "events.orders"
        group = "processors"
    }.collect { event ->
        println("Processor A: ${String(event.body)}")
    }
}

launch {
    pubsub.subscribeToEvents {
        channel = "events.orders"
        group = "processors"
    }.collect { event ->
        println("Processor B: ${String(event.body)}")
    }
}
```

## Best Practices

1. **Use descriptive group names:** `"order-processors"`, `"notification-handlers"`
2. **Scale horizontally:** Add more group members to increase throughput
3. **Monitor with metrics:** Track message distribution across workers
4. **Handle errors per worker:** Each worker should handle its own errors independently
5. **Use consistent group names:** All members of a group must use the exact same group string

## Related

- [API Reference: EventsSubscriptionConfig](https://kubemq.github.io/kubemq-kotlin/io.kubemq.sdk.pubsub/-events-subscription-config/)
- [Concepts Guide](../concepts.md)
- [Examples: Consumer Groups](../examples/src/main/kotlin/io/kubemq/sdk/examples/events/ConsumerGroupExample.kt)
