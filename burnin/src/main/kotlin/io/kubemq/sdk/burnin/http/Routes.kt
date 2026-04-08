package io.kubemq.sdk.burnin.http

import io.kubemq.sdk.burnin.RunConfig
import io.kubemq.sdk.burnin.RunState
import io.kubemq.sdk.burnin.Runner
import io.kubemq.sdk.burnin.toApiString
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
                call.respondText(
                    """{"status":"alive"}""",
                    ContentType.Application.Json,
                )
            }

            get("/ready") {
                val state = runner.currentState.toApiString()
                val status = when (runner.currentState) {
                    RunState.STARTING, RunState.STOPPING -> "not_ready"
                    else -> "ready"
                }
                val code = when (runner.currentState) {
                    RunState.STARTING, RunState.STOPPING -> HttpStatusCode.ServiceUnavailable
                    else -> HttpStatusCode.OK
                }
                call.respondText(
                    """{"status":"$status","state":"$state"}""",
                    ContentType.Application.Json,
                    code,
                )
            }

            get("/info") {
                call.respondText(
                    runner.getInfoJson().toString(),
                    ContentType.Application.Json,
                )
            }

            get("/broker/status") {
                call.respondText(
                    runner.brokerStatusJson().toString(),
                    ContentType.Application.Json,
                )
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
                    val rid = result.getOrDefault("")
                    call.respondText(
                        """{"run_id": "$rid", "state": "starting", "message": "Run started"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
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
                    val status = runner.status()
                    call.respondText(
                        """{"run_id": "${status.runId}", "state": "${status.state}", "message": "Run stopping"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
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

            get("/run/report") {
                val report = runner.getReport()
                if (report != null) {
                    call.respondText(
                        report.toString(),
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                } else {
                    call.respondText(
                        """{"error": "No report available"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound,
                    )
                }
            }

            get("/run") {
                call.respond(runner.status())
            }

            get("/run/config") {
                call.respondText(
                    """{"status":"ok"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK,
                )
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
