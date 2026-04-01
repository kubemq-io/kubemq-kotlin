package io.kubemq.sdk.burnin

import io.kubemq.sdk.burnin.http.startHttpServer
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

fun main() {
    val logger = LoggerFactory.getLogger("burnin")

    val brokerAddress = System.getenv("KUBEMQ_BROKER_ADDRESS")
        ?: System.getenv("KUBEMQ_ADDRESS")
        ?: "localhost:50000"

    val port = System.getenv("BURNIN_PORT")?.toIntOrNull() ?: 8888

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
