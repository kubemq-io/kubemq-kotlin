package io.kubemq.sdk.observability

internal object OTelAvailability {
    val isAvailable: Boolean by lazy {
        try {
            Class.forName("io.opentelemetry.api.OpenTelemetry")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
