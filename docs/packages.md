# Module KubeMQ Kotlin SDK

Kotlin-native SDK for the KubeMQ message broker, built with coroutines, Flow, and DSL builders.

# Package io.kubemq.sdk.client

Core client types and connection configuration. Contains [KubeMQClient] (base class with
factory methods), [ClientConfig], [ConnectionState], reconnection settings, and buffer configuration.

# Package io.kubemq.sdk.pubsub

Publish/subscribe messaging with events and events store. Contains [PubSubClient] for
publishing and subscribing, message types ([EventMessage], [EventStoreMessage]), subscription
configuration, and the [StartPosition] sealed interface for events store replay.

# Package io.kubemq.sdk.cq

Commands and queries (request-response patterns). Contains [CQClient] for sending commands/queries
and subscribing to incoming requests, message types ([CommandMessage], [QueryMessage]),
response types, and subscription configuration.

# Package io.kubemq.sdk.queues

Queue messaging with guaranteed delivery. Contains [QueuesClient] with both stream and simple APIs,
[QueueMessage] for sending, [QueueReceivedMessage] with ack/reject/reQueue operations,
and delivery policy configuration.

# Package io.kubemq.sdk.exception

Typed exception hierarchy for all SDK errors. [KubeMQException] is a sealed class with subtypes
for connection, authentication, authorization, timeout, validation, throttling, transport, server,
client-closed, and stream-broken errors. Includes [ErrorCode] and [ErrorCategory] for
programmatic error handling.

# Package io.kubemq.sdk.common

Shared types used across the SDK: [ChannelInfo] and [ChannelStats] for channel management,
[ChannelType] enum, and [ServerInfo] returned by ping operations.

# Package io.kubemq.sdk.transport

Transport layer configuration. Contains [TlsConfig] for TLS/mTLS settings.
The internal [Transport] interface is not part of the public API.

# Package io.kubemq.sdk.observability

Optional observability interfaces for logging, metrics, and tracing. Implement [KubeMQLogger],
[KubeMQMetrics], or [KubeMQTracing] for custom backends. OpenTelemetry implementations are
provided when the OTel SDK is on the classpath.

# Package io.kubemq.sdk.retry

Retry policy configuration for automatic retry of transient failures. Use [retryPolicy] DSL
builder to create a [RetryPolicy] with exponential backoff and jitter.
