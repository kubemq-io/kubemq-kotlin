package io.kubemq.sdk.burnin.report

import io.kubemq.sdk.burnin.RunConfig
import io.kubemq.sdk.burnin.patterns.BasePattern

object Reporter {

    fun generateVerdict(patterns: List<BasePattern>, config: RunConfig?): String {
        if (patterns.isEmpty()) return "NO_PATTERNS"

        var totalSent = 0L
        var totalReceived = 0L
        var totalErrors = 0L
        val patternResults = mutableListOf<String>()

        for (pattern in patterns) {
            val s = pattern.status()
            totalSent += s.sent
            totalReceived += s.received
            totalErrors += s.errors

            val lossPct = if (s.sent > 0) {
                ((s.sent - s.received).toDouble() / s.sent * 100)
            } else {
                0.0
            }

            val errorPct = if (s.sent > 0) {
                (s.errors.toDouble() / s.sent * 100)
            } else {
                0.0
            }

            patternResults.add(
                "${s.name}: sent=${s.sent} recv=${s.received} err=${s.errors} loss=%.2f%% errRate=%.2f%%".format(
                    lossPct, errorPct,
                ),
            )
        }

        val overallLossPct = if (totalSent > 0) {
            ((totalSent - totalReceived).toDouble() / totalSent * 100)
        } else {
            0.0
        }

        val overallErrorPct = if (totalSent > 0) {
            (totalErrors.toDouble() / totalSent * 100)
        } else {
            0.0
        }

        val maxLoss = config?.thresholds?.maxLossPct ?: 1.0
        val maxError = config?.thresholds?.maxErrorRatePct ?: 1.0

        val pass = overallLossPct <= maxLoss && overallErrorPct <= maxError

        val sb = StringBuilder()
        sb.appendLine(if (pass) "PASS" else "FAIL")
        sb.appendLine("Total: sent=$totalSent recv=$totalReceived err=$totalErrors loss=%.2f%% errRate=%.2f%%".format(
            overallLossPct, overallErrorPct,
        ))
        for (pr in patternResults) {
            sb.appendLine("  $pr")
        }

        return sb.toString().trim()
    }
}
