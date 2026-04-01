package io.kubemq.sdk.common

import io.kubemq.sdk.client.JitterType
import kotlin.math.pow
import kotlin.random.Random

internal object BackoffComputation {

    fun compute(
        attempt: Int,
        initialBackoffMs: Long,
        maxBackoffMs: Long,
        multiplier: Double,
        jitter: JitterType,
    ): Long {
        val cappedMs = (initialBackoffMs.toDouble() * multiplier.pow((attempt - 1).toDouble()))
            .coerceAtMost(maxBackoffMs.toDouble()).toLong()
        return when (jitter) {
            JitterType.NONE -> cappedMs
            JitterType.FULL -> {
                val lowerBound = (initialBackoffMs / 2).coerceAtMost(cappedMs)
                Random.nextLong(lowerBound, cappedMs + 1)
            }
            JitterType.EQUAL -> cappedMs / 2 + Random.nextLong(0, cappedMs / 2 + 1)
        }
    }
}
