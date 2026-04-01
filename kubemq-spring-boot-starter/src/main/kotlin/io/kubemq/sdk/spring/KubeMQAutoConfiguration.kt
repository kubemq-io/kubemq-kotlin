package io.kubemq.sdk.spring

import io.kubemq.sdk.client.ClientConfig
import io.kubemq.sdk.client.KubeMQClient
import io.kubemq.sdk.client.LogLevel
import io.kubemq.sdk.cq.CQClient
import io.kubemq.sdk.pubsub.PubSubClient
import io.kubemq.sdk.queues.QueuesClient
import io.kubemq.sdk.transport.TlsConfig
import java.util.logging.Logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KubeMQProperties::class)
@ConditionalOnProperty(prefix = "kubemq", name = ["address"])
class KubeMQAutoConfiguration {

    private val logger = Logger.getLogger(KubeMQAutoConfiguration::class.java.name)

    @Bean
    @ConditionalOnMissingBean
    fun pubSubClient(props: KubeMQProperties): PubSubClient = KubeMQClient.pubSub {
        applyProperties(props)
    }

    @Bean
    @ConditionalOnMissingBean
    fun queuesClient(props: KubeMQProperties): QueuesClient = KubeMQClient.queues {
        applyProperties(props)
    }

    @Bean
    @ConditionalOnMissingBean
    fun cqClient(props: KubeMQProperties): CQClient = KubeMQClient.cq {
        applyProperties(props)
    }

    private fun ClientConfig.applyProperties(props: KubeMQProperties) {
        address = props.address
        clientId = props.clientId
        authToken = props.authToken
        logLevel = try {
            LogLevel.valueOf(props.logLevel.uppercase())
        } catch (_: IllegalArgumentException) {
            logger.warning("Invalid logLevel '${props.logLevel}', falling back to INFO")
            LogLevel.INFO
        }
        if (props.tls) {
            tls = TlsConfig().apply {
                certFile = props.tlsCertFile
                keyFile = props.tlsKeyFile
                caCertFile = props.caCertFile
            }
        }
    }
}
