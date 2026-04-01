package io.kubemq.sdk.exception

/**
 * Error codes corresponding to gRPC status codes.
 *
 * Used in [KubeMQException.code] to categorize errors programmatically.
 *
 * @see KubeMQException
 */
public enum class ErrorCode {
    /** Operation completed successfully (should not appear in exceptions). */
    OK,
    /** The operation was cancelled (typically by the caller). */
    CANCELLED,
    /** Unknown error (catch-all for unmapped server errors). */
    UNKNOWN,
    /** Client specified an invalid argument (e.g., blank channel name). */
    INVALID_ARGUMENT,
    /** Deadline expired before operation could complete. */
    DEADLINE_EXCEEDED,
    /** Requested entity (e.g., channel) was not found. */
    NOT_FOUND,
    /** Entity that the client attempted to create already exists. */
    ALREADY_EXISTS,
    /** The caller does not have permission to execute the operation. */
    PERMISSION_DENIED,
    /** Resource has been exhausted (e.g., rate limit). */
    RESOURCE_EXHAUSTED,
    /** Operation rejected because the system is not in a required state. */
    FAILED_PRECONDITION,
    /** Operation was aborted (typically due to a concurrency conflict). */
    ABORTED,
    /** Operation was attempted past the valid range. */
    OUT_OF_RANGE,
    /** Operation is not implemented or not supported. */
    UNIMPLEMENTED,
    /** Internal errors (unexpected server-side failures). */
    INTERNAL,
    /** The service is currently unavailable (transient condition). */
    UNAVAILABLE,
    /** Unrecoverable data loss or corruption. */
    DATA_LOSS,
    /** Request does not have valid authentication credentials. */
    UNAUTHENTICATED,
}
