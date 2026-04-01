package io.kubemq.sdk.exception

import io.grpc.Status
import io.grpc.StatusRuntimeException

internal fun StatusRuntimeException.toKubeMQException(
    operation: String,
    channel: String? = null,
    requestId: String? = null,
    isClientCancelled: Boolean = false,
): KubeMQException = when (status.code) {
    Status.Code.OK ->
        KubeMQException.Server("Unexpected OK status", this, operation = operation)

    Status.Code.CANCELLED ->
        if (isClientCancelled) {
            KubeMQException.ClientClosed()
        } else {
            KubeMQException.Server(
                "Operation cancelled by server", this,
                code = ErrorCode.CANCELLED, operation = operation,
            )
        }

    Status.Code.UNKNOWN ->
        KubeMQException.Server(
            status.description ?: "Unknown error", this,
            operation = operation,
        )

    Status.Code.INVALID_ARGUMENT ->
        KubeMQException.Validation(
            status.description ?: "Invalid argument",
            operation = operation, channel = channel,
        )

    Status.Code.DEADLINE_EXCEEDED ->
        KubeMQException.Timeout(
            status.description ?: "Deadline exceeded", this,
            operation = operation, channel = channel, requestId = requestId,
        )

    Status.Code.NOT_FOUND ->
        KubeMQException.Server(
            status.description ?: "Not found", this,
            code = ErrorCode.NOT_FOUND, operation = operation, channel = channel,
        )

    Status.Code.ALREADY_EXISTS ->
        KubeMQException.Validation(
            status.description ?: "Already exists",
            code = ErrorCode.ALREADY_EXISTS, operation = operation, channel = channel,
        )

    Status.Code.PERMISSION_DENIED ->
        KubeMQException.Authorization(
            status.description ?: "Permission denied", this,
            operation, channel,
        )

    Status.Code.RESOURCE_EXHAUSTED ->
        KubeMQException.Throttling(
            status.description ?: "Resource exhausted", this,
            operation, channel,
        )

    Status.Code.FAILED_PRECONDITION ->
        KubeMQException.Validation(
            status.description ?: "Failed precondition",
            code = ErrorCode.FAILED_PRECONDITION, operation = operation, channel = channel,
        )

    Status.Code.ABORTED ->
        KubeMQException.Server(
            status.description ?: "Aborted", this,
            code = ErrorCode.ABORTED, category = ErrorCategory.TRANSIENT,
            isRetryable = true, operation = operation, channel = channel,
        )

    Status.Code.OUT_OF_RANGE ->
        KubeMQException.Validation(
            status.description ?: "Out of range",
            code = ErrorCode.OUT_OF_RANGE, operation = operation, channel = channel,
        )

    Status.Code.UNIMPLEMENTED ->
        KubeMQException.Server(
            status.description ?: "Unimplemented", this,
            code = ErrorCode.UNIMPLEMENTED, operation = operation,
        )

    Status.Code.INTERNAL ->
        KubeMQException.Transport(
            status.description ?: "Internal error", this,
            operation = operation,
        )

    Status.Code.UNAVAILABLE ->
        KubeMQException.Connection(
            status.description ?: "Service unavailable", this,
            operation,
        )

    Status.Code.DATA_LOSS ->
        KubeMQException.Transport(
            status.description ?: "Data loss", this,
            code = ErrorCode.DATA_LOSS, operation = operation,
        )

    Status.Code.UNAUTHENTICATED ->
        KubeMQException.Authentication(
            status.description ?: "Unauthenticated", this,
            operation,
        )

    else ->
        KubeMQException.Server(
            "Unknown gRPC status: ${status.code}", this,
            operation = operation,
        )
}
