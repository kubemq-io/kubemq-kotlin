package io.kubemq.sdk.burnin

import com.charleskorn.kaml.Yaml
import io.kubemq.sdk.burnin.http.startHttpServer
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("burnin")

    val brokerAddress = System.getenv("KUBEMQ_BROKER_ADDRESS")
        ?: System.getenv("KUBEMQ_ADDRESS")
        ?: "localhost:50000"

    val configPath = run {
        val idx = args.indexOf("--config")
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }

    val port = if (configPath != null) {
        val startupConfig = Yaml.default.decodeFromString(StartupConfig.serializer(), File(configPath).readText())
        startupConfig.metrics.port
    } else {
        System.getenv("BURNIN_PORT")?.toIntOrNull() ?: 8888
    }

    logger.info("KubeMQ Kotlin Burn-in starting on port {} with broker {}", port, brokerAddress)

    val runner = Runner(brokerAddress)
    startHttpServer(runner, port)

    logger.info("Burn-in server idle, waiting for POST /run/start")

    // Handle graceful shutdown
    val latch = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received, cleaning up...")
        kotlinx.coroutines.runBlocking {
            try {
                runner.stop()
                runner.cleanup()
            } catch (e: Exception) {
                logger.warn("Cleanup during shutdown: {}", e.message)
            }
        }
        latch.countDown()
    })

    latch.await()
}
