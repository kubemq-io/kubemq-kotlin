package io.kubemq.sdk.client

/**
 * DSL marker annotation for KubeMQ builder APIs.
 *
 * Prevents accidental access to outer receiver scopes when using nested DSL builders.
 * Applied to all builder classes ([ClientConfig], [EventMessageBuilder], etc.) and
 * subscription config classes.
 */
@DslMarker
public annotation class KubeMQDsl
