package io.kubemq.sdk.exception

/**
 * Base exception type for all KubeMQ SDK errors.
 *
 * [KubeMQException] is a sealed class, enabling exhaustive `when` matching:
 * ```kotlin
 * try {
 *     client.ping()
 * } catch (e: KubeMQException) {
 *     when (e) {
 *         is KubeMQException.Connection -> println("Connection lost: ${e.message}")
 *         is KubeMQException.Authentication -> println("Bad credentials")
 *         is KubeMQException.Authorization -> println("Permission denied on ${e.channel}")
 *         is KubeMQException.Timeout -> println("Operation timed out")
 *         is KubeMQException.Validation -> println("Invalid input: ${e.message}")
 *         is KubeMQException.Throttling -> println("Rate limited, retry later")
 *         is KubeMQException.Transport -> println("Transport error: ${e.message}")
 *         is KubeMQException.Server -> println("Server error: ${e.message}")
 *         is KubeMQException.ClientClosed -> println("Client was closed")
 *         is KubeMQException.StreamBroken -> println("Stream interrupted")
 *     }
 * }
 * ```
 *
 * @property code The [ErrorCode] categorizing this error (maps to gRPC status codes)
 * @property category The [ErrorCategory] (TRANSIENT, PERMANENT, or FATAL)
 * @property isRetryable `true` if the operation can be safely retried
 * @property operation The SDK operation that triggered this error (e.g., "sendCommand")
 * @property channel The channel involved, if applicable
 * @property requestId The request ID involved, if applicable
 * @property serverAddress The broker address, if applicable
 * @see ErrorCode
 * @see ErrorCategory
 */
public sealed class KubeMQException(
    message: String,
    cause: Throwable? = null,
    public val code: ErrorCode,
    public val category: ErrorCategory,
    public val isRetryable: Boolean,
    public val operation: String? = null,
    public val channel: String? = null,
    public val requestId: String? = null,
    public val serverAddress: String? = null,
) : RuntimeException(message, cause) {

    /** Thrown when the client cannot reach the KubeMQ broker. Maps to gRPC `UNAVAILABLE`. Retryable. */
    public class Connection(
        message: String,
        cause: Throwable? = null,
        operation: String? = null,
        serverAddress: String? = null,
    ) : KubeMQException(
        message, cause, ErrorCode.UNAVAILABLE, ErrorCategory.TRANSIENT, true,
        operation, serverAddress = serverAddress,
    )

    /** Thrown when authentication fails (invalid or missing auth token). Maps to gRPC `UNAUTHENTICATED`. Not retryable. */
    public class Authentication(
        message: String,
        cause: Throwable? = null,
        operation: String? = null,
        serverAddress: String? = null,
    ) : KubeMQException(
        message, cause, ErrorCode.UNAUTHENTICATED, ErrorCategory.PERMANENT, false,
        operation, serverAddress = serverAddress,
    )

    /** Thrown when the client lacks permission for the requested operation. Maps to gRPC `PERMISSION_DENIED`. Not retryable. */
    public class Authorization(
        message: String,
        cause: Throwable? = null,
        operation: String? = null,
        channel: String? = null,
    ) : KubeMQException(
        message, cause, ErrorCode.PERMISSION_DENIED, ErrorCategory.PERMANENT, false,
        operation, channel,
    )

    /**
     * Thrown when an operation exceeds its deadline. Maps to gRPC `DEADLINE_EXCEEDED`. Retryable.
     *
     * @property duration The timeout duration that was exceeded, if available.
     */
    public class Timeout(
        message: String,
        cause: Throwable? = null,
        public val duration: kotlin.time.Duration? = null,
        operation: String? = null,
        channel: String? = null,
        requestId: String? = null,
    ) : KubeMQException(
        message, cause, ErrorCode.DEADLINE_EXCEEDED, ErrorCategory.TRANSIENT, true,
        operation, channel, requestId,
    )

    /** Thrown when input parameters fail validation (blank channel, invalid timeout, etc.). Maps to gRPC `INVALID_ARGUMENT`. Not retryable. */
    public class Validation(
        message: String,
        code: ErrorCode = ErrorCode.INVALID_ARGUMENT,
        operation: String? = null,
        channel: String? = null,
    ) : KubeMQException(
        message, null, code, ErrorCategory.PERMANENT, false,
        operation, channel,
    )

    /** Thrown when the broker is rate-limiting the client. Maps to gRPC `RESOURCE_EXHAUSTED`. Retryable after backoff. */
    public class Throttling(
        message: String,
        cause: Throwable? = null,
        operation: String? = null,
        channel: String? = null,
    ) : KubeMQException(
        message, cause, ErrorCode.RESOURCE_EXHAUSTED, ErrorCategory.TRANSIENT, true,
        operation, channel,
    )

    /** Thrown for low-level transport errors (gRPC internal error, data loss). Maps to gRPC `INTERNAL` or `DATA_LOSS`. Fatal. */
    public class Transport(
        message: String,
        cause: Throwable? = null,
        code: ErrorCode = ErrorCode.INTERNAL,
        operation: String? = null,
    ) : KubeMQException(
        message, cause, code, ErrorCategory.FATAL, false,
        operation,
    )

    /**
     * Thrown for server-side errors not covered by other subtypes.
     *
     * @property statusCode The gRPC status code value, if available.
     */
    public class Server(
        message: String,
        cause: Throwable? = null,
        public val statusCode: Int? = null,
        code: ErrorCode = ErrorCode.UNKNOWN,
        category: ErrorCategory = ErrorCategory.FATAL,
        isRetryable: Boolean = false,
        operation: String? = null,
        channel: String? = null,
    ) : KubeMQException(
        message, cause, code, category, isRetryable,
        operation, channel,
    )

    /** Thrown when an operation is attempted on a closed client. Fatal. */
    public class ClientClosed(
        message: String = "Client is closed",
    ) : KubeMQException(
        message, null, ErrorCode.CANCELLED, ErrorCategory.FATAL, false,
    )

    /** Thrown when a gRPC stream is unexpectedly terminated. Maps to gRPC `UNAVAILABLE`. Retryable. */
    public class StreamBroken(
        message: String,
        cause: Throwable? = null,
        operation: String? = null,
        channel: String? = null,
    ) : KubeMQException(
        message, cause, ErrorCode.UNAVAILABLE, ErrorCategory.TRANSIENT, true,
        operation, channel,
    )
}
