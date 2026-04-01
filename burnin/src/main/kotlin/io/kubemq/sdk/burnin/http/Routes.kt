package io.kubemq.sdk.burnin.http

import io.kubemq.sdk.burnin.RunConfig
import io.kubemq.sdk.burnin.Runner
import io.kubemq.sdk.burnin.metrics.Metrics
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun startHttpServer(runner: Runner, port: Int = 8888) {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(json)
        }

        routing {
            get("/health") {
                call.respondText("ok", ContentType.Text.Plain)
            }

            get("/ready") {
                call.respondText("ready", ContentType.Text.Plain)
            }

            get("/info") {
                call.respond(
                    mapOf(
                        "sdk" to "kubemq-kotlin",
                        "version" to "1.0.0",
                        "runtime" to System.getProperty("java.version"),
                        "state" to runner.currentState.name.lowercase(),
                    ),
                )
            }

            get("/broker/status") {
                val result = runner.brokerStatus()
                if (result.isSuccess) {
                    call.respondText(result.getOrDefault("unknown"), ContentType.Text.Plain)
                } else {
                    call.respondText(
                        "error: ${result.exceptionOrNull()?.message}",
                        ContentType.Text.Plain,
                        HttpStatusCode.ServiceUnavailable,
                    )
                }
            }

            post("/run/start") {
                val body = call.receiveText()
                val config = try {
                    json.decodeFromString<RunConfig>(body)
                } catch (e: Exception) {
                    call.respondText(
                        """{"error": "Invalid config: ${e.message}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@post
                }

                val result = runner.start(config)
                if (result.isSuccess) {
                    call.respondText(
                        """{"status": "started", "run_id": "${result.getOrDefault("")}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                } else {
                    call.respondText(
                        """{"error": "${result.exceptionOrNull()?.message}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                }
            }

            post("/run/stop") {
                val result = runner.stop()
                if (result.isSuccess) {
                    call.respondText(
                        """{"status": "stopped"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                } else {
                    call.respondText(
                        """{"error": "${result.exceptionOrNull()?.message}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                }
            }

            get("/run/status") {
                call.respond(runner.status())
            }

            post("/cleanup") {
                val result = runner.cleanup()
                if (result.isSuccess) {
                    call.respondText(
                        """{"status": "cleaned up"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                } else {
                    call.respondText(
                        """{"error": "${result.exceptionOrNull()?.message}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }

            get("/metrics") {
                call.respondText(Metrics.scrape(), ContentType.Text.Plain)
            }
        }
    }.start(wait = false)
}
