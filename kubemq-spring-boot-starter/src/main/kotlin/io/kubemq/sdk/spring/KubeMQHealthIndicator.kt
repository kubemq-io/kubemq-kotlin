package io.kubemq.sdk.spring

import io.kubemq.sdk.pubsub.PubSubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass

@ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
class KubeMQHealthIndicator(private val client: PubSubClient) : HealthIndicator {
    override fun health(): Health = try {
        val info = runBlocking(Dispatchers.IO) { client.ping() }
        Health.up()
            .withDetail("host", info.host)
            .withDetail("version", info.version)
            .build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
