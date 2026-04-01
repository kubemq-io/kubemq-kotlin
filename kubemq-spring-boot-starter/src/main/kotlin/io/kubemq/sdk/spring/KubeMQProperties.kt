package io.kubemq.sdk.spring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kubemq")
class KubeMQProperties {
    var address: String = "localhost:50000"
    var clientId: String = ""
    var authToken: String = ""
    var tls: Boolean = false
    var tlsCertFile: String = ""
    var tlsKeyFile: String = ""
    var caCertFile: String = ""
    var logLevel: String = "INFO"
}
