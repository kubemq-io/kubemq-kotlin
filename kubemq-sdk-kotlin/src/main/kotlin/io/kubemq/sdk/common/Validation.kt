package io.kubemq.sdk.common

import io.kubemq.sdk.exception.KubeMQException

internal object Validation {
    private val CHANNEL_PATTERN = Regex("^[a-zA-Z0-9._\\-/:>*]+$")
    private const val MAX_CHANNEL_LENGTH = 256

    fun validateChannel(channel: String, operation: String) {
        if (channel.isBlank()) {
            throw KubeMQException.Validation("Channel name is required", operation = operation)
        }
        if (channel.length > MAX_CHANNEL_LENGTH) {
            throw KubeMQException.Validation(
                "Channel name exceeds $MAX_CHANNEL_LENGTH chars",
                operation = operation, channel = channel,
            )
        }
        if (channel.endsWith(".")) {
            throw KubeMQException.Validation(
                "Channel cannot end with '.'",
                operation = operation, channel = channel,
            )
        }
        if (!CHANNEL_PATTERN.matches(channel)) {
            throw KubeMQException.Validation(
                "Channel contains invalid characters. Allowed: alphanumeric, dots, dashes, " +
                    "underscores, forward slashes, colons, wildcards. Got: '$channel'",
                operation = operation, channel = channel,
            )
        }
    }

    fun validateChannelNoWildcard(channel: String, operation: String) {
        validateChannel(channel, operation)
        if (channel.contains("*") || channel.contains(">")) {
            throw KubeMQException.Validation(
                "Wildcards not allowed for $operation",
                operation = operation, channel = channel,
            )
        }
    }

    fun validateNotEmpty(value: String, name: String, operation: String) {
        if (value.isBlank()) {
            throw KubeMQException.Validation("$name is required", operation = operation)
        }
    }

    fun validateBodyOrMetadata(metadata: String, body: ByteArray, operation: String) {
        if (metadata.isEmpty() && body.isEmpty()) {
            throw KubeMQException.Validation(
                "At least one of metadata or body is required",
                operation = operation,
            )
        }
    }

    fun validatePositive(value: Int, name: String, operation: String) {
        if (value <= 0) {
            throw KubeMQException.Validation("$name must be > 0", operation = operation)
        }
    }

    fun validateRange(value: Int, min: Int, max: Int, name: String, operation: String) {
        if (value < min || value > max) {
            throw KubeMQException.Validation(
                "$name must be between $min and $max",
                operation = operation,
            )
        }
    }
}
