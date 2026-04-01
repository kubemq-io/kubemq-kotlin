package io.kubemq.sdk.observability

/**
 * A scope wrapping an active trace span. Must be [closed][close] when the operation completes.
 *
 * Use in a `use` block for automatic cleanup:
 * ```kotlin
 * tracing.startSpan("my-operation").use { span ->
 *     span.setAttribute("channel", "orders")
 *     // ... do work ...
 *     span.setStatus(true)
 * }
 * ```
 *
 * @see KubeMQTracing.startSpan
 */
public interface SpanScope : java.io.Closeable {
    /**
     * Adds a key-value attribute to this span.
     *
     * @param key Attribute key
     * @param value Attribute value
     */
    public fun setAttribute(key: String, value: String)

    /**
     * Records an error on this span.
     *
     * @param throwable The exception to record
     */
    public fun recordError(throwable: Throwable)

    /**
     * Sets the final status of this span.
     *
     * @param success `true` for OK status, `false` for ERROR status
     */
    public fun setStatus(success: Boolean)

    /** Ends this span and restores the previous span context. */
    override fun close()
}
