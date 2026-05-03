package week04.task3.api

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import week04.task3.config.JsonConfig
import week04.task3.mcp.McpClientBridge
import week04.task3.model.HistoryResponse
import week04.task3.model.SummaryResponse
import week04.task3.model.ToolCallPayload
import week04.task3.model.StatusResponse
import week04.task3.service.SchedulerService
import week04.task3.ui.buildUiPage

fun Application.configureTask3Routes(
    scheduler: SchedulerService,
    mcpClient: McpClientBridge,
) {
    install(ContentNegotiation) {
        json(JsonConfig.json)
    }

    routing {
        get("/") {
            call.respondText(buildUiPage(), ContentType.Text.Html)
        }

        get("/api/task3/status") {
            call.respond(
                StatusResponse(
                    mcpConnected = mcpClient.isConnected(),
                    jobState = scheduler.state(),
                    summary = scheduler.summary(),
                ),
            )
        }

        get("/api/task3/summary") {
            call.respond(SummaryResponse(summary = scheduler.summary(), jobState = scheduler.state()))
        }

        get("/api/task3/history") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            call.respond(HistoryResponse(items = scheduler.history(limit)))
        }

        get("/api/task3/mcp/tools") {
            call.respond(mapOf("tools" to mcpClient.listTools()))
        }

        post("/api/task3/run-now") {
            call.respond(scheduler.runNow())
        }

        post("/api/task3/mcp/tool-call") {
            val payload = call.receive<ToolCallPayload>()
            val result = mcpClient.callTool(payload.name, payload.arguments)
            call.respond(mapOf("result" to result))
        }
    }
}
